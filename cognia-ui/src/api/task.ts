import { http } from './http'
import type { ScheduledTask, TaskRunResult, UpdateScheduledTaskPayload } from '@/types/api'

export const taskApi = {
  list(): Promise<ScheduledTask[]> {
    return http.get<ScheduledTask[]>('/scheduled-tasks')
  },
  update(code: string, payload: UpdateScheduledTaskPayload): Promise<ScheduledTask> {
    return http.put<ScheduledTask>(`/scheduled-tasks/${code}`, payload)
  },
  run(code: string): Promise<TaskRunResult> {
    return http.post<TaskRunResult>(`/scheduled-tasks/${code}/run`)
  },
}
