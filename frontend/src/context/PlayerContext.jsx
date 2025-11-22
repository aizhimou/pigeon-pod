import React, { createContext, useState, useContext, useCallback } from 'react';

const PlayerContext = createContext(null);

export const PlayerProvider = ({ children }) => {
  const [currentEpisode, setCurrentEpisode] = useState(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMaximized, setIsMaximized] = useState(false);
  const [feedTitle, setFeedTitle] = useState('');

  const play = useCallback((episode, feed) => {
    setCurrentEpisode(episode);
    setFeedTitle(feed?.title || '');
    setIsPlaying(true);
    // If it's video, we might want to maximize by default or start minimized.
    // Requirement says: "If video content... provide a button to center... can also be minimized".
    // So it should probably start in a default state (maybe minimized or just the bar).
    // But for video, a "preview window" is mentioned.
    setIsMaximized(false);
  }, []);

  const pause = useCallback(() => {
    setIsPlaying(false);
  }, []);

  const close = useCallback(() => {
    setCurrentEpisode(null);
    setIsPlaying(false);
    setIsMaximized(false);
  }, []);

  const toggleMaximize = useCallback(() => {
    setIsMaximized((prev) => !prev);
  }, []);

  const minimize = useCallback(() => {
    setIsMaximized(false);
  }, []);

  return (
    <PlayerContext.Provider
      value={{
        currentEpisode,
        feedTitle,
        isPlaying,
        isMaximized,
        play,
        pause,
        close,
        toggleMaximize,
        minimize,
      }}
    >
      {children}
    </PlayerContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const usePlayer = () => {
  const context = useContext(PlayerContext);
  if (!context) {
    throw new Error('usePlayer must be used within a PlayerProvider');
  }
  return context;
};
