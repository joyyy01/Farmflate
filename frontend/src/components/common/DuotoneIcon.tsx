import React from 'react';

export type CropIconType = 'lettuce' | 'potato' | 'pear' | 'cucumber' | 'apple' | 'carrot' | 'tomato' | 'pepper' | 'corn' | 'cabbage' | 'sprout' | string;

interface CropIconProps {
  type: CropIconType;
  bgSize?: number;
  iconSize?: number;
  size?: number;
}

/* Pure Vector SVG Asset Map */
const VECTOR_SVG_ASSETS: Record<string, string> = {
  lettuce: '/svg-assets/crops/lettuce.svg',
  potato: '/svg-assets/crops/potato.svg',
  pear: '/svg-assets/crops/pear.svg',
  cabbage: '/svg-assets/crops/cabbage.svg',
  pepper: '/svg-assets/crops/pepper.svg',
  tomato: '/svg-assets/crops/tomato.svg',
  sprout: '/svg-assets/crops/sprout.svg',
  leaf: '/svg-assets/crops/leaf.svg',
  soil: '/svg-assets/crops/soil-sprout.svg',
  apple: '/assets/apple.png',
  cucumber: '/assets/cucumber.png',
  corn: '/assets/corn.png'
};

const FALLBACK_3D_ASSETS: Record<string, string> = {
  lettuce: 'https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Leafy%20green/3D/leafy_green_3d.png',
  potato: 'https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Potato/3D/potato_3d.png',
  pear: 'https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Pear/3D/pear_3d.png',
  cucumber: 'https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Cucumber/3D/cucumber_3d.png',
  apple: 'https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Red%20apple/3D/red_apple_3d.png',
};

export const DuotoneIcon: React.FC<CropIconProps> = ({
  type,
  bgSize = 48,
  iconSize = 38,
  size
}) => {
  const actualIconSize = size || iconSize;
  const vectorUrl = VECTOR_SVG_ASSETS[type] || VECTOR_SVG_ASSETS.lettuce;
  const fallbackUrl = FALLBACK_3D_ASSETS[type] || FALLBACK_3D_ASSETS.lettuce;

  return (
    <div
      style={{
        width: bgSize,
        height: bgSize,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0
      }}
    >
      <img
        src={vectorUrl}
        alt={type}
        style={{
          width: actualIconSize,
          height: actualIconSize,
          objectFit: 'contain',
          filter: 'drop-shadow(0 3px 8px rgba(47, 168, 106, 0.15))',
          transition: 'transform 0.2s ease'
        }}
        onError={(e) => {
          const imgEl = e.target as HTMLImageElement;
          if (imgEl.src !== fallbackUrl) {
            imgEl.src = fallbackUrl;
          }
        }}
      />
    </div>
  );
};
