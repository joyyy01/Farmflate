// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { BackButton } from '../components/common/BackButton';

describe('BackButton', () => {
  it('uses the shared back icon asset and invokes the supplied action', () => {
    const onClick = vi.fn();
    const { container } = render(<BackButton onClick={onClick} />);

    fireEvent.click(screen.getByRole('button', { name: '뒤로가기' }));

    expect(onClick).toHaveBeenCalledOnce();
    expect(container.querySelector('img[src="/svg-assets/ui-icons/back.svg"]')).toBeInTheDocument();
  });
});
