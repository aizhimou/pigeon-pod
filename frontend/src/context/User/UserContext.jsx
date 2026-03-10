import React from 'react';
import { initialState } from './UserReducer.js';

export const UserContext = React.createContext({
  state: initialState,
  dispatch: () => null,
  authEnabled: true,
});
