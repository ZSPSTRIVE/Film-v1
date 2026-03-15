import request from '@/utils/request'

export interface LoginDTO {
  username: string
  password: string
}

export interface RegisterDTO {
  username: string
  password: string
  email?: string
  phone?: string
}

export interface UserInfo {
  id: number
  username: string
  nickname?: string
  avatar?: string
  role: string
  email?: string
  phone?: string
}

export const authApi = {
  login(data: LoginDTO) {
    return request.post<any, { code: number; msg: string; data: string }>('/v1/user/login', data)
  },
  register(data: RegisterDTO) {
    return request.post<any, { code: number; msg: string; data: any }>('/v1/user/register', data)
  },
  getUserInfo() {
    return request.get<any, { code: number; msg: string; data: UserInfo }>('/v1/user/info')
  },
  logout() {
    return request.post<any, { code: number; msg: string; data: any }>('/v1/user/logout')
  }
}
