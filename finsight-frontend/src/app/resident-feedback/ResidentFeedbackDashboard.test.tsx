import { render, screen, waitFor } from '@testing-library/react';
import ResidentFeedbackDashboard from './page';
import { apiFetch } from '@/lib/api';
import { vi, describe, it, expect, beforeEach } from 'vitest';

// Helper to mock a successful JSON response
const mockJsonResponse = (data: any) => {
  return Promise.resolve({
    ok: true,
    json: () => Promise.resolve(data),
  });
};

describe('ResidentFeedbackDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    
    // Default mock: return current survey and dashboard data
    (apiFetch as any).mockImplementation((url: string) => {
      if (url.includes('/survey/current')) {
        return mockJsonResponse({ id: 1, quarter: 'Q1', year: 2024, status: 'ACTIVE' });
      }
      if (url.includes('/survey/dashboard')) {
        return mockJsonResponse({
          totalResponses: 5,
          averageRatings: { 'Facility A': 4.5 },
          insights: [
            { facility: 'Facility A', sentimentScore: 0.9, aiSummary: 'Great', recommendations: 'Keep it up' }
          ],
          executiveSummary: 'Overall excellent performance.'
        });
      }
      return mockJsonResponse({});
    });
  });

  it('renders the executive summary and insights correctly', async () => {
    render(<ResidentFeedbackDashboard />);

    // Wait for the data to load and the executive summary to appear
    await waitFor(() => {
      expect(screen.getByText(/Executive Board Summary/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/Overall excellent performance/i)).toBeInTheDocument();
    expect(screen.getByText(/Operational Action Items/i)).toBeInTheDocument();
    expect(screen.getByText(/Facility A/i)).toBeInTheDocument();
  });

  it('shows the save report button', async () => {
    render(<ResidentFeedbackDashboard />);
    
    await waitFor(() => {
      expect(screen.getByText(/Save Report/i)).toBeInTheDocument();
    });
  });
});
