import React from 'react';
import { Card, Group, Text } from '@mantine/core';

/**
 * Statistics Card Component
 *
 * @param {Object} props
 * @param {string} props.label - The label text for the card
 * @param {number} props.count - The count number to display
 * @param {React.ReactNode} props.icon - The icon component
 * @param {string} props.color - The color for the count text (Mantine color)
 */
const StatisticsCard = ({ label, count, icon, color}) => {
  return (
    <Card radius="md" withBorder>
      <Group justify="space-between" mb="xs">
        <Text c="dimmed" >
          {label}
        </Text>
        {React.cloneElement(icon, {
          size: 20,
        })}
      </Group>
      <Text size="1.5rem" fw={600} c={color} >
        {count}
      </Text>
    </Card>
  );
};

export default StatisticsCard;

