import React from 'react';
import { userReducer, initialState } from './UserReducer.js';
import { UserContext } from './UserContext';

export const UserProvider = ({ children }) => {
  const [state, dispatch] = React.useReducer(userReducer, initialState);

  // hydrate user from localStorage on mount
  React.useEffect(() => {
    try {
      const stored = localStorage.getItem('user');
      if (stored) {
        const parsed = JSON.parse(stored);
        if (parsed) {
          dispatch({ type: 'login', payload: parsed });
        }
      }
    } catch {
      // ignore malformed storage
    }
  }, []);

  return <UserContext.Provider value={[state, dispatch]}>{children}</UserContext.Provider>;
};
