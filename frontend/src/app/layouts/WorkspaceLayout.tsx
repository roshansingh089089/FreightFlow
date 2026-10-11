import { createContext, useContext } from 'react';

export type WorkspaceMode = 'mail' | 'split' | 'platform';
export const WorkspaceLayoutContext = createContext<{
  mode: WorkspaceMode;
  setMode: (mode: WorkspaceMode) => void;
}>({ mode: 'split', setMode: () => {} });
export const useWorkspaceLayout = () => useContext(WorkspaceLayoutContext);
