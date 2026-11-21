import type { CaptchaSolution, CaptchaSubmission } from '@/types/api'

/**
 * Strips the UI-only provider marker before submitting a challenge result.
 * The backend always verifies against the provider configured in the console.
 */
export function toCaptchaSubmission(solution: CaptchaSolution | null): CaptchaSubmission | undefined {
  if (!solution) {
    return undefined
  }
  const { provider: _provider, ...submission } = solution
  return submission
}
