import { useContext } from 'react';
import { UserContext } from '../context/User/UserContext';
import { DEFAULT_DATE_FORMAT } from '../constants/dateFormats';

/**
 * 获取当前用户的日期格式偏好
 * @returns {string} 日期格式字符串
 */
export const useDateFormat = () => {
  const [state] = useContext(UserContext);
  return state?.user?.dateFormat || DEFAULT_DATE_FORMAT;
};
