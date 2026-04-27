import os
import uuid
from datetime import datetime
from typing import Dict, List
from zoneinfo import ZoneInfo
import threading
import asyncio
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.date import DateTrigger
from apscheduler.triggers.interval import IntervalTrigger

from api_server.utils.scheduler import scheduler, get_all_jobs_info, delete_job
from api_server.utils.scheduler_parser import ParseError, nature_time
from api_server.utils.rabbitmq_producer import publish_to_topic_exchange
from tools.pgsql_manger import DefaultAsyncPgsqlDBManager
from api_server.utils import logger

SECONDS_PER_MINUTE = 60
SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY
# 可以继续扩展，例如年和月
# 年和月的秒数不固定，这里使用平均值或直接按天计算会更精确

UNIT_TO_SECONDS_MAP = {
    'days': SECONDS_PER_DAY,
    'weeks': SECONDS_PER_WEEK,
    'hours': SECONDS_PER_HOUR,
    'minutes': SECONDS_PER_MINUTE,
}


def repeat_to_seconds(repeat):
    """
    将一个包含时间单位和数量的字典转换成总秒数。
    :param repeat: 一个字典，键是时间单位（如 'weeks'），
                         值是相应的数量。
                         例如: {'weeks': 2}
    :return: 计算出的总秒数。如果字典为空或格式错误，则返回0。
    """
    total_seconds = 0
    # 使用 items() 遍历字典的键和值
    for unit, value in repeat.items():
        # 检查单位是否在我们的已知单位映射中
        if unit in UNIT_TO_SECONDS_MAP:
            # 确保值是一个数字（int 或 float）
            if isinstance(value, (int, float)):
                total_seconds += value * UNIT_TO_SECONDS_MAP[unit]
            else:
                raise ParseError(f"警告: 单位 '{unit}' 的值 '{value}' 不是数字，已忽略。")
        else:
            # 如果单位未知，打印一个警告
            raise ParseError(f"警告: 未知的时间单位 '{unit}'，已忽略。")

    return total_seconds


async def repeat2trigger(repeat, reminder_time, description):
    """
    将repeat、reminder_time转化为trigger
    :param repeat:
    :param reminder_time:
    :param description:
    :return:
    """
    now = datetime.now(ZoneInfo('Asia/Shanghai'))
    if repeat:
        if 'years' in repeat or 'months' in repeat or 'days' in repeat:
            trigger_type = 'cron'
            # 周期性任务
            # 根据repeat参数创建CronTrigger
            cron_params = {}
            if 'years' in repeat:
                # 每几年执行一次，使用具体的年份列表而非 */N 表达式
                cron_params['month'] = reminder_time.month
                cron_params['day'] = reminder_time.day
                cron_params['hour'] = reminder_time.hour
                cron_params['minute'] = reminder_time.minute

                # 如果是多年间隔，从当前年份开始计算
                if repeat['years'] > 1:
                    start_year = now.year
                    # 构造年份列表，确保包含当前年份及以后的年份
                    year_list = []
                    for i in range(20):  # 生成未来40年内的年份（足够使用）
                        year = start_year + i * repeat['years']
                        year_list.append(str(year))
                    cron_params['year'] = ','.join(year_list)
                else:
                    # 每年重复
                    cron_params['year'] = '*'

            elif 'months' in repeat:
                # 每几个月执行一次
                cron_params['day'] = reminder_time.day
                cron_params['hour'] = reminder_time.hour
                cron_params['minute'] = reminder_time.minute
                if repeat['months'] > 1:
                    cron_params['month'] = '*/{}'.format(repeat['months'])
                else:
                    cron_params['month'] = '*'
            elif 'days' in repeat:
                cron_params['year'] = '*'
                cron_params['month'] = '*'
                cron_params['hour'] = reminder_time.hour
                cron_params['minute'] = reminder_time.minute
                if repeat['days'] > 1:
                    cron_params['day'] = '*/{}'.format(repeat['days'])
                else:
                    cron_params['day'] = '*'

            trigger = CronTrigger(**cron_params)
        else:
            trigger_type = 'interval'
            trigger = IntervalTrigger(start_date=now, seconds=repeat_to_seconds(repeat))
    else:
        # 一次性任务，若识别到的提醒时间在当前时间之后，则设置为定时任务，否则告知用户设置的有问题
        if now > reminder_time:
            raise ParseError(
                f"{reminder_time.strftime('%Y-%m-%d %H:%M:%S')}已经过去了，请重设一个将来的提醒。\n\n消息: {description}")
        trigger_type = 'date'
        trigger = DateTrigger(run_date=reminder_time.strftime("%Y-%m-%d %H:%M:%S"))

    return {'trigger_type': trigger_type, 'trigger': trigger}


def get_repeat_msg(duration: Dict):
    if not duration:
        return ''
    key, value = next(iter(duration.items()))
    if not isinstance(value, int) or value < 0:
        raise ValueError(f"Invalid value for duration: {value}. The value should be a non-negative integer.")
    if key == "years":
        return f"重复: {value}年"
    elif key == "months":
        return f"重复: {value}个月"
    elif key == "weeks":
        return f"重复: {value}周"
    elif key == "days":
        return f"重复: {value}天"
    elif key == "hours":
        return f"重复: {value}小时"
    elif key == "minutes":
        return f"重复: {value}分钟"
    else:
        raise ValueError(f"Unknown duration unit: {key}. Supported units are: years, months, days, hours, minutes.")


async def add_reminder(who, reminder_time, repeat, description, event, client_username, nickname):
    """
    添加定时提醒任务
    1. 添加scheduler定时任务
    2. 定时任务信息存储到库中（便于项目重启后，自动恢复定时任务）
    :param who: 用户名: 微信名
    :param reminder_time:
    :param repeat:
    :param description:
    :param event:
    :param client_username:
    :param nickname:
    :return:
    """
    trigger_info = await repeat2trigger(repeat, reminder_time, description)
    trigger_type = trigger_info.get("trigger_type")
    trigger = trigger_info.get("trigger")
    task_id = str(uuid.uuid4())
    try:
        job = scheduler.add_job(
            func=execute_reminder,
            trigger=trigger,
            id=task_id,
            name=f"{who}_{description}",
            kwargs={"task_id": task_id, "event": event, "description": description, "who": who, "repeat": repeat,
                    "trigger_type": trigger_type, "reminder_time": reminder_time, "client_username": client_username,
                    "nickname": nickname}
        )
        add_info = {'status': 1, 'msg': '', 'next_run_time': job.next_run_time}
    except Exception as e:
        msg = f"apscheduler添加定时任务错误: {e}"
        logger.error(msg)
        add_info = {'status': 0, 'msg': msg}

    # 发送微信进行响应提醒添加成功或失败
    try:
        if add_info.get("status") == 1:
            nature_time_msg = nature_time(add_info.get('next_run_time'))
            repeat_msg = get_repeat_msg(repeat)
            content = (f"✅将在 {nature_time_msg} 提醒你{event}\n\n"
                       f"备注: {description}\n"
                       f"下次提醒时间: {add_info.get('next_run_time').strftime('%Y-%m-%d %H:%M')}\n"
                       f"{repeat_msg}")
            return {"status": 1, "msg": "提醒添加成功！", "content": content}
        else:
            content = (f"❌提醒添加失败！无法理解你刚才说的话！\n\n"
                       f"或者您可以换个姿势告诉我该怎么定时🐥，比如这样: \n\n"
                       f'两个星期后提醒我去复诊\n'
                       f'周五晚上提醒我打电话给老妈\n'
                       f'每月20号提醒我还信用卡[捂脸]')
            return {"status": 1, "msg": "提醒添加失败！", "content": content}
    except Exception as e:
        msg = f"添加提醒未知错误: {e}"
        logger.error(msg)
        return {"status": 0, "msg": msg}


def execute_reminder(task_id, event, description, who, repeat, trigger_type, client_username, nickname, **kwargs):
    """
    执行定时提醒任务
    :param task_id:
    :return:
    """
    # 发送微信进行提醒
    repeat_msg = get_repeat_msg(repeat)
    next_run_time_msg = ''
    if trigger_type != 'date':
        job = scheduler.get_job(job_id=task_id)
        next_run_time = job.next_run_time
        nature_time_msg = nature_time(next_run_time)
        next_run_time_msg = f"下次提醒时间: {nature_time_msg} {next_run_time.strftime('%Y-%m-%d %H:%M')}\n"

    reminder_msg = (f"🕒 {event}\n\n"
                    f"备注: {description}\n"
                    f"{next_run_time_msg}"
                    f"{repeat_msg}")
    exchange_name = "T_REMINDER"
    routing_key = f"{client_username}.{nickname}.remind"
    payload = {"who": who, "msg": reminder_msg}
    message_headers = {"msg_type": "remind"}

    def run_async_publish():
        asyncio.run(publish_to_topic_exchange(routing_key, payload, exchange_name,
                                              persistent=True, message_headers=message_headers,
                                              expiration=30 * 60 * 1000))

    thread = threading.Thread(target=run_async_publish)
    thread.start()

    return {"status": 1, "msg": "提醒执行成功", "content": reminder_msg}


def get_all_reminders():
    """
    获取所有定时任务
    :return:
    """
    return get_all_jobs_info(scheduler)


async def search_all_reminders_by_who(who: str) -> List[Dict]:
    """
    查找某人的所有提醒，按照创建时间降序排序
    :param who:
    :return:
    """
    import pandas as pd

    sql = "select * from reminder_scheduler where who=%s order by create_time desc"
    async with DefaultAsyncPgsqlDBManager() as db:
        db_user_reminders = await db.execute_query(sql, [who])

    all_reminders = get_all_reminders()
    scheduler_user_reminders = [reminder for reminder in all_reminders if reminder.get('kwargs').get('who', '') == who]

    df_db = pd.DataFrame(db_user_reminders)
    df_scheduler = pd.DataFrame(scheduler_user_reminders)
    if not df_db.empty and not df_scheduler.empty:
        df_merge = pd.merge(df_scheduler, df_db, how='left', left_on=['id'], right_on=['task_id'])
        df_merge = df_merge.sort_values(by='create_time', ascending=False)  # 根据创建提醒的时间，降序排序
        df_merge.reset_index(drop=True)
        result = df_merge.to_dict('records')
        return result
    return []


async def get_all_reminders_by_who(who) -> Dict:
    self_reminders = await search_all_reminders_by_who(who)
    if self_reminders:
        # 发送微信消息进行响应
        reminders = [item.get('description') for item in self_reminders]
        reminders_msg = [f"{i + 1}. {reminder}\n" for i, reminder in enumerate(reminders) if reminder]
        self_all_reminders = (f"🕒 {who}的提醒任务如下:\n\n"
                              f"{''.join(reminders_msg)}\n"
                              f"⚠️删除提醒\n"
                              f"发送：删除提醒<序号>\n"
                              f"例如：删除提醒1")
        return {"status": 1, "msg": "", "content": self_all_reminders}
    return {"status": 0, "msg": "查不到提醒数据"}


async def delete_reminder(who: str, num: int):
    try:
        if not isinstance(num, int):
            return {"status": 0, "msg": "输入的序号非数字！"}

        self_reminders = await search_all_reminders_by_who(who)
        if self_reminders:
            # 发送微信消息进行响应
            task_id = self_reminders[num - 1].get('task_id')
            delete_info = delete_job(task_id)
            if delete_info['status'] == 1:
                del_msg = f"✅已删除提醒任务！\n"
                self_reminders = await search_all_reminders_by_who(who)
                if self_reminders:
                    reminders = [item.get('description') for item in self_reminders]
                    reminders_msg = [f"{i + 1}. {reminder}\n" for i, reminder in enumerate(reminders) if reminder]
                    del_msg += (f"🕒 {who}的提醒任务如下:\n\n"
                                f"{''.join(reminders_msg)}")
                else:
                    del_msg += f"🕒 {who}没有提醒任务了！"
                return {"status": 1, "msg": "删除成功！", "content": del_msg}
            else:
                return delete_info
        else:
            return {"status": 0, "msg": "没有找到该用户提醒任务！"}
    except Exception as e:
        return {"status": 0, "msg": f"删除提醒任务未知错误: {e}"}


if __name__ == '__main__':
    async def main():
        # 启动调度器以便从数据库加载任务
        scheduler.start()
        # 获取所有提醒任务
        await get_all_reminders_by_who("文件传输助手")
        # 关闭调度器
        scheduler.shutdown()


    asyncio.run(main())
