import React, { useRef, useEffect } from 'react';
import { usePlayer } from '../../context/PlayerContext';
import Plyr from 'plyr-react';
import 'plyr-react/plyr.css';
import {
  Box,
  Paper,
  Group,
  Text,
  Image,
  CloseButton,
  Modal,
  ActionIcon,
  useMantineColorScheme,
} from '@mantine/core';
import { useMediaQuery, useDisclosure } from '@mantine/hooks';
import { IconX } from '@tabler/icons-react';

const GlobalPlayer = () => {
  const { currentEpisode, feedTitle, isPlaying, close } = usePlayer();
  const isSmallScreen = useMediaQuery('(max-width: 36em)');
  const plyrRef = useRef(null);
  const [videoModalOpened, { open: openVideoModal, close: closeVideoModal }] = useDisclosure(false);
  const { colorScheme } = useMantineColorScheme();
  const isDark = colorScheme === 'dark';

  // Responsive modal width
  const isSmall = useMediaQuery('(max-width: 48em)');
  const isLarge = useMediaQuery('(min-width: 75em)');
  const modalWidth = isSmall ? '95%' : isLarge ? '75%' : '85%';

  // Determine media type.
  const isVideo = currentEpisode?.mediaType?.startsWith('video') || false;

  // Auto-open modal for video
  useEffect(() => {
    if (isVideo && currentEpisode) {
      openVideoModal();
    }
  }, [currentEpisode, isVideo, openVideoModal]);

  // Construct source
  const source = React.useMemo(
    () =>
      currentEpisode
        ? {
            type: isVideo ? 'video' : 'audio',
            sources: [
              {
                src: `/media/${currentEpisode.id}.${isVideo ? 'mp4' : 'm4a'}`,
                type: currentEpisode.enclosureType || (isVideo ? 'video/mp4' : 'audio/mp3'),
              },
            ],
            poster: isVideo ? currentEpisode.maxCoverUrl || currentEpisode.defaultCoverUrl : null,
          }
        : null,
    [currentEpisode, isVideo],
  );

  useEffect(() => {
    if (currentEpisode && isPlaying) {
      // Auto play logic if needed
    }
  }, [currentEpisode, isPlaying]);

  const videoOptions = React.useMemo(
    () => ({
      autoplay: true,
      controls: [
        'play-large',
        'play',
        'progress',
        'current-time',
        'mute',
        'volume',
        'captions',
        'settings',
        'airplay',
        'fullscreen',
      ],
    }),
    [],
  );

  const audioOptions = React.useMemo(
    () => ({
      autoplay: true,
      controls: [
        'play-large',
        'play',
        'progress',
        'current-time',
        'mute',
        'volume',
        'captions',
        'settings',
        'pip',
        'airplay',
        'fullscreen',
      ],
      ratio: null, // Disable aspect ratio for audio
      fullscreen: { enabled: false }, // Disable fullscreen for audio
    }),
    [],
  );

  if (!currentEpisode) return null;

  const handleClosePlayer = () => {
    closeVideoModal();
    close();
  };

  const handleOpenModal = () => {
    openVideoModal();
  };

  return (
    <>
      {/* Video Modal */}
      <Modal
        opened={videoModalOpened}
        onClose={handleClosePlayer} // Closing modal stops playback
        size="auto" // Use auto size to allow custom width via styles
        centered
        withCloseButton={false}
        closeOnClickOutside={false}
        padding={0}
        styles={{
          body: { backgroundColor: 'transparent' },
          content: {
            backgroundColor: 'transparent',
            boxShadow: 'none',
            width: modalWidth,
            maxWidth: 'none', // Override default max-width
            flex: '0 0 auto', // Ensure it doesn't shrink
          },
        }}
      >
        <Box
          style={{
            position: 'relative',
            border: '1px solid rgba(255,255,255,0.2)',
            borderRadius: '8px',
            overflow: 'hidden',
            backgroundColor: 'black',
          }}
        >
          {/* Modal Controls */}
          <Box
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              height: '50px',
              zIndex: 10,
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
              padding: '0 10px',
              pointerEvents: 'none', // Let clicks pass through to player if not on buttons
            }}
          >
            <Group gap="xs" style={{ pointerEvents: 'auto' }}>
              <ActionIcon
                variant="subtle"
                color="gray"
                onClick={handleClosePlayer}
                aria-label="Close"
              >
                <IconX size={20} color="white" />
              </ActionIcon>
            </Group>
          </Box>

          {/* Render Plyr in Modal ONLY if it is a video */}
          {isVideo && (
            <Plyr
              key="video-modal"
              ref={plyrRef}
              source={source}
              options={videoOptions}
              style={{
                '--plyr-color-main': '#228be6',
              }}
            />
          )}
        </Box>
      </Modal>

      {/* Bottom Player Bar - Only show if NOT video */}
      {!isVideo && (
        <Paper
          shadow="md"
          p={0}
          style={{
            position: 'fixed',
            bottom: 0,
            left: 0,
            right: 0,
            zIndex: 200,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'flex-end',
            backgroundColor: 'var(--mantine-color-body)',
            borderTop: `1px solid ${isDark ? 'var(--mantine-color-dark-4)' : 'var(--mantine-color-gray-3)'}`,
            transition: 'all 0.3s ease',
          }}
        >
          <Box
            style={{
              width: '100%',
              margin: '0 auto',
              display: 'flex',
              flexDirection: isSmallScreen ? 'column' : 'row',
              alignItems: 'center', // Fix alignment
            }}
          >
            {/* Info Section */}
            <Group
              pl={isSmallScreen ? 'sm' : '0'}
              pr="sm"
              pt={isSmallScreen ? 'xs' : '0'}
              pb="0"
              style={{
                flex: isSmallScreen ? 1 : '0 1 auto',
                minWidth: 0,
                width: isSmallScreen ? '100%' : 'auto',
                maxWidth: isSmallScreen ? '100%' : '40%', // Limit width on desktop too
                marginRight: isSmallScreen ? 0 : '1rem',
                cursor: isVideo ? 'pointer' : 'default',
              }}
              wrap="nowrap"
              onClick={isVideo ? handleOpenModal : undefined}
            >
              <Image
                src={currentEpisode.maxCoverUrl || currentEpisode.defaultCoverUrl}
                h={50}
                w={50}
                radius="sm"
                fallbackSrc="https://placehold.co/250x250?text=No+Image"
                style={{ transition: 'transform 0.2s' }}
              />
              <Box style={{ minWidth: 0, flex: 1 }}>
                <Text size="sm" fw={500} truncate>
                  {currentEpisode.title}
                </Text>
                <Text size="xs" c="dimmed" truncate>
                  {feedTitle}
                </Text>
              </Box>
            </Group>

            {/* Player Section & Close Button Wrapper */}
            <Box
              style={{
                display: 'flex',
                alignItems: 'center',
                width: isSmallScreen ? '100%' : 'auto',
                flex: isSmallScreen ? 'auto' : 2,
              }}
            >
              {/* Player Section - Only show if NOT video (Audio only in bottom bar) */}
              <Box
                style={{
                  flex: 1,
                  width: '100%',
                }}
              >
                <Plyr
                  key="audio-bar"
                  ref={plyrRef}
                  source={source}
                  options={audioOptions}
                  style={{
                    '--plyr-color-main': '#228be6',
                    '--plyr-audio-controls-background': 'transparent',
                    '--plyr-audio-control-color': isDark ? '#C1C2C5' : '#495057',
                    '--plyr-menu-background': isDark ? '#25262b' : '#fff',
                    '--plyr-menu-color': isDark ? '#C1C2C5' : '#495057',
                  }}
                />
              </Box>

              {/* Close Button */}
              <CloseButton
                onClick={close}
                size="md"
                mr={isSmallScreen ? 'xs' : 'md'}
                ml={isSmallScreen ? -10 : 'xs'}
              />
            </Box>
          </Box>
        </Paper>
      )}
    </>
  );
};

export default GlobalPlayer;
