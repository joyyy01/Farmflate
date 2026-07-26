import type { CSSProperties } from 'react';

interface BackButtonProps {
  onClick: () => void;
  disabled?: boolean;
  className?: string;
  style?: CSSProperties;
  ariaLabel?: string;
}

export const BackButton = ({
  onClick,
  disabled = false,
  className,
  style,
  ariaLabel = '뒤로가기'
}: BackButtonProps) => (
  <button
    type="button"
    onClick={onClick}
    disabled={disabled}
    aria-label={ariaLabel}
    className={className}
    style={{
      background: 'none',
      border: 'none',
      cursor: disabled ? 'default' : 'pointer',
      padding: 0,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      opacity: disabled ? 0.4 : 1,
      ...style
    }}
  >
    <img src="/svg-assets/ui-icons/back.svg" alt="" width={24} height={24} />
  </button>
);
