import React from 'react';

interface IconSvgProps {
  name: string;
  size?: number;
  color?: string;
  className?: string;
  style?: React.CSSProperties;
}

export const IconSvg: React.FC<IconSvgProps> = ({
  name,
  size = 24,
  color = 'currentColor',
  className = '',
  style = {}
}) => {
  return (
    <svg
      className={`uicon ${className}`}
      style={{
        width: size,
        height: size,
        color,
        display: 'inline-block',
        verticalAlign: 'middle',
        ...style
      }}
    >
      <use href={`/assets/icons.svg#${name}`} />
    </svg>
  );
};
