import React from 'react';
import { initialState } from './UserReducer.js';

export const UserContext = React.createContext([
  initialState,
  () => null,
]);
