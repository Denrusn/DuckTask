import json
import os

from apscheduler.events import EVENT_JOB_ERROR, EVENT_JOB_REMOVED, EVENT_JOB_ADDED
from apscheduler.jobstores.base import JobLookupError
from apscheduler.jobstores.sqlalchemy import SQLAlchemyJobStore
from apscheduler.schedulers.background import BackgroundScheduler
from dotenv import load_dotenv

from tools.pgsql_manger import DefaultPgsqlDBManager
from . import logger

# 加载环境变量
load_dotenv()

database_url = (
    f'postgresql+psycopg2://'
    f'{os.getenv("DB_USER")}:{os.getenv("DB_PASSWORD")}'
    f'@{os.getenv("DB_HOST")}:{os.getenv("DB_PORT")}/{os.getenv("DB_NAME")}'
)
# 创建调度器实例
jobstores = {
    'default': SQLAlchemyJobStore(url=database_url),
}
executors = {
    'default': {
        'type': 'threadpool',
        'max_workers': 20  # 原默认仅10，新闻推送需更高并发
    }
}
job_defaults = {
    'coalesce': True,  # 合并错过的触发
    'max_instances': 3,  # 防止堆积任务阻塞调度
    'misfire_grace_time': 60 * 5  # **核心修改：将默认1秒宽限期提升至30秒**
}
scheduler = BackgroundScheduler(
    jobstores=jobstores,
    executors=executors,
    job_defaults=job_defaults,
    timezone='Asia/Shanghai'
)


# 2. 定义事件监听器 (Callback)
def job_listener(event):
    """
    当任务成功执行完毕时，此函数会被自动调用。
    event.code == EVENT_JOB_EXECUTED (值为 210)
    """
    job_id = event.job_id
    if event.code == EVENT_JOB_ADDED:
        job = get_job_info_by_id(job_id)
        logger.info(
            f"\n[监听器] 收到任务添加成功事件! Job ID: {job_id} Description：{job.get('kwargs').get('description')}")
        add_info = add_pg_reminder(**job.get("kwargs"))
        if add_info['status'] == 1:
            logger.info(
                f"[监听器] 添加定时任务成功! Job ID: {job_id} Description：{job.get('kwargs').get('description')}")
        else:
            logger.error(
                f"[监听器] 添加定时任务失败! Job ID: {job_id} Description：{job.get('kwargs').get('description')}")
    elif event.code == EVENT_JOB_REMOVED:
        logger.info(f"\n[监听器] 收到任务被删除事件! Job ID: {job_id}")
        delete_info = delete_pg_reminder(job_id)
        if delete_info['status'] == 1:
            logger.info(f"[监听器] 删除定时任务成功! Job ID: {job_id}")
        else:
            logger.error(f"[监听器] 删除定时任务失败! Job ID: {job_id}")


# 3. 定义任务出错时的监听器 (用于清理)
def job_error_listener(event):
    """
    当任务执行失败时，也可以做一些清理工作。
    """
    if event.code == EVENT_JOB_ERROR:
        job_id = event.job_id
        logger.error(f"\n[监听器] 收到任务执行失败事件! Job ID: {job_id}")


def add_pg_reminder(task_id, event, description, who, reminder_time, repeat, trigger_type, **kwargs):
    # 插入提醒数据
    with DefaultPgsqlDBManager() as db:
        sql = ('INSERT INTO reminder_scheduler (who,reminder_time,repeat,description,event,trigger_type,task_id,type,'
               'client_username,nickname) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) ')
        status = db.execute_update(sql, [who, reminder_time, json.dumps(repeat),
                                         description, event, trigger_type, task_id,
                                         kwargs.get("type"), kwargs.get("client_username"), kwargs.get("nickname")])

        if status != -1:
            msg = 'pg提醒添加成功'
            add_info = {'status': 1, 'msg': msg}
        else:
            msg = 'pg提醒数据库插入有误'
            add_info = {'status': 0, 'msg': msg}
        return add_info


def delete_pg_reminder(task_id):
    """
    删除PG数据库总的提醒任务记录
    :param task_id:
    :return:
    """
    with DefaultPgsqlDBManager() as db:
        # 获取定时任务信息
        sql = 'DELETE FROM reminder_scheduler WHERE task_id = %s'
        affected_rows = db.execute_update(sql, (task_id,))
        if affected_rows != -1:
            return {'status': 1, 'msg': 'pg提醒删除成功'}
        else:
            return {'status': 0, 'msg': 'pg提醒删除错误'}


def get_all_jobs_info(scheduler: BackgroundScheduler) -> list:
    """
    从APScheduler实例中获取所有任务的信息，并格式化为字典列表。

    Args:
        scheduler: AsyncIOScheduler实例。

    Returns:
        一个字典列表，每个字典代表一个任务，包含其详细信息。
        例如: [{'id': 'job-1', 'name': '任务一', 'next_run_time': '...', ...}, ...]
    """
    jobs_info = []

    for job in scheduler.get_jobs():
        # 2. 通过job_id获取具体的Job对象

        if job:
            # 3. 从Job对象中提取信息并构建字典
            job_dict = get_job_info(job)
            jobs_info.append(job_dict)

    return jobs_info


def get_job_info_by_id(job_id: str):
    job = scheduler.get_job(job_id)
    return get_job_info(job)


def get_job_info(job):
    return {
        'id': job.id,
        'name': job.name,
        'func_name': str(job.func),  # 函数的字符串表示
        'trigger_class_name': job.trigger.__class__.__name__,
        'trigger_str': str(job.trigger),
        'next_run_time': job.next_run_time.strftime("%Y-%m-%d %H:%M:%S") if job.next_run_time else None,
        'misfire_grace_time': job.misfire_grace_time,
        'coalesce': job.coalesce,
        'max_instances': job.max_instances,
        'args': job.args,
        'kwargs': job.kwargs,
        'status': 'scheduled' if job.next_run_time else 'completed'  # 简单的状态判断
    }


def delete_job(job_id):
    """
    删除定时任务
    :param job_id:
    :return:
    """
    try:
        scheduler.remove_job(job_id)
        return {'status': 1, 'msg': '定时任务删除成功'}
    except JobLookupError:
        return {'status': 0, 'msg': '定时任务不存在'}
    except Exception as e:
        return {'status': 0, 'msg': f"删除定时任务位置错误: {str(e)}"}


# 添加监听器，监听添加和删除事件
scheduler.add_listener(
    job_listener,
    EVENT_JOB_ADDED | EVENT_JOB_REMOVED
)
scheduler.add_listener(
    job_error_listener,
    EVENT_JOB_ERROR
)
