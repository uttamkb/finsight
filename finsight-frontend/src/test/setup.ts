import { beforeAll, vi } from 'vitest';
import '@testing-library/jest-dom';

// Standard mocks for Next.js and external libs
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
  usePathname: () => '',
}));

vi.mock('@/lib/api', () => ({
  apiFetch: vi.fn(),
}));

vi.mock('@/components/toast-provider', () => ({
  useToast: () => ({
    toast: vi.fn(),
  }),
}));
