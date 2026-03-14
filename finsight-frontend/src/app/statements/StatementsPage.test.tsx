import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import StatementsPage from './page';
import { apiFetch } from '@/lib/api';
import { vi, describe, it, expect, beforeEach } from 'vitest';

// Helper to mock a successful JSON response
const mockJsonResponse = (data: any) => {
  return Promise.resolve({
    ok: true,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(JSON.stringify(data)),
  });
};

describe('StatementsPage - Live Upload Progress', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default mock for initial page load
    (apiFetch as any).mockImplementation((url: string) => {
        if (url.includes('/statements/transactions')) return mockJsonResponse({ content: [], totalPages: 0 });
        if (url.includes('/reconciliation/audit-trail')) return mockJsonResponse([]);
        if (url.includes('/receipts')) return mockJsonResponse({ content: [] });
        if (url.includes('/settings')) return mockJsonResponse({ currency: 'INR' });
        return mockJsonResponse({});
    });
  });

  it('renders the initial header correctly', () => {
    render(<StatementsPage />);
    expect(screen.getByText(/Upload Statements/i)).toBeInTheDocument();
  });

  it('shows the processing pipeline when an upload starts', async () => {
    render(<StatementsPage />);
    
    // Mock the POST upload response
    (apiFetch as any).mockImplementationOnce((url: string, init: any) => {
      if (url === '/statements/upload' && init.method === 'POST') {
        return mockJsonResponse({ status: 'RUNNING' });
      }
      return mockJsonResponse({});
    });

    const file = new File(['dummy content'], 'statement.pdf', { type: 'application/pdf' });
    const input = screen.getByLabelText(/Upload Statement/i) || document.querySelector('input[type="file"]');
    
    await act(async () => {
      fireEvent.change(input!, { target: { files: [file] } });
    });

    // Check if Pipeline header appears
    expect(screen.getByText(/Statement Processing Pipeline/i)).toBeInTheDocument();
    expect(screen.getByText(/Secure Upload & Init/i)).toBeInTheDocument();
  });

  it('transitions through pipeline stages based on status polling', async () => {
    vi.useFakeTimers();
    render(<StatementsPage />);

    // 1. Start upload
    (apiFetch as any).mockImplementationOnce((url: string) => url === '/statements/upload' ? mockJsonResponse({ status: 'RUNNING' }) : mockJsonResponse({}));
    const file = new File(['hello'], 'test.csv', { type: 'text/csv' });
    const input = document.querySelector('input[type="file"]');
    await act(async () => { fireEvent.change(input!, { target: { files: [file] } }); });

    // 2. Mock first poll: EXTRACTION stage
    (apiFetch as any).mockImplementationOnce((url: string) => {
      if (url === '/statements/upload/status') {
        return mockJsonResponse({
          status: 'RUNNING',
          stage: 'EXTRACTION',
          message: 'Extracting data...',
          totalFiles: 10,
          processedFiles: 0
        });
      }
      return mockJsonResponse({});
    });

    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(screen.getByText(/AI Data Extraction/i)).toBeInTheDocument();
    expect(screen.getByText(/Parsed 10 transactions/i)).toBeInTheDocument();

    // 3. Mock second poll: PERSISTENCE stage
    (apiFetch as any).mockImplementationOnce((url: string) => {
      if (url === '/statements/upload/status') {
        return mockJsonResponse({
          status: 'RUNNING',
          stage: 'PERSISTENCE',
          message: 'Saving to DB...',
          totalFiles: 10,
          processedFiles: 5
        });
      }
      return mockJsonResponse({});
    });

    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(screen.getByText(/Database Verification/i)).toBeInTheDocument();
    expect(screen.getByText(/Successfully Saved/i).parentElement?.querySelector('.text-emerald-500')).toHaveTextContent('5');

    // 4. Final poll: SUCCESS
    (apiFetch as any).mockImplementationOnce((url: string) => {
      if (url === '/statements/upload/status') {
        return mockJsonResponse({
          status: 'SUCCESS',
          stage: 'COMPLETED',
          message: 'Done',
          totalFiles: 10,
          processedFiles: 10
        });
      }
      return mockJsonResponse({});
    });

    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(screen.getByText(/Upload Finished/i)).toBeInTheDocument();

    vi.useRealTimers();
  });
});
