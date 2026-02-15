import React from 'react';
import { Card, Group, Text } from '@mantine/core';
import './StatisticsCard.css';

/**
 * Statistics Card Component
 *
 * @param {Object} props
 * @param {string} props.label - The label text for the card
 * @param {number} props.count - The count number to display
 * @param {React.ReactNode} props.icon - The icon component
 * @param {string} props.color - The color for the count text (Mantine color)
 */
const StatisticsCard = ({ label, count, icon, color, onClick }) => {
  const clickable = typeof onClick === 'function';

  return (
    <Card
      radius="sm"
      withBorder
      onClick={onClick}
      className={clickable ? 'statistics-card-clickable' : undefined}
      style={clickable ? { cursor: 'pointer' } : undefined}
    >
      <Group justify="space-between" mb="xs">
        <Text c="dimmed">{label}</Text>
        {icon
          ? React.cloneElement(icon, {
              size: 20,
            })
          : null}
      </Group>
      <Text size="1.5rem" fw={600} c={color}>
        {count}
      </Text>
    </Card>
  );
};

export default StatisticsCard;
