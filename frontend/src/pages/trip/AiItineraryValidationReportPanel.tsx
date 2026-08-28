import type { AiItineraryValidationReport, ValidationIssue } from '../../api/itineraryValidation'
import './AiItineraryValidationReportPanel.css'

type AiItineraryValidationReportPanelProps = {
  report: AiItineraryValidationReport | null
}

export function AiItineraryValidationReportPanel({ report }: AiItineraryValidationReportPanelProps) {
  if (!report) {
    return null
  }

  const totalCount = report.errors.length + report.warnings.length + report.unverifiedConditions.length

  return (
    <section className="ai-validation-report" aria-live="polite">
      {totalCount === 0 ? (
        <p className="ai-validation-empty">All verifiable itinerary conditions passed.</p>
      ) : (
        <>
          <IssueGroup title="Errors" tone="error" issues={report.errors} />
          <IssueGroup title="Warnings" tone="warning" issues={report.warnings} />
          <IssueGroup title="Unverified conditions" tone="info" issues={report.unverifiedConditions} />
        </>
      )}
    </section>
  )
}

function IssueGroup({
  issues,
  title,
  tone,
}: {
  issues: ValidationIssue[]
  title: string
  tone: 'error' | 'warning' | 'info'
}) {
  if (issues.length === 0) {
    return null
  }

  return (
    <div className={`ai-validation-group ${tone}`}>
      <strong>{title}</strong>
      <ul>
        {issues.map((issue, index) => (
          <li key={`${issue.code}-${issue.path ?? 'root'}-${issue.placeId ?? 'none'}-${index}`}>
            <span>{issue.message}</span>
            <small>{issueSummary(issue)}</small>
            {issue.relatedTargets.length > 0 && (
              <ul className="ai-validation-related">
                {issue.relatedTargets.map((target, targetIndex) => (
                  <li key={`${target.path ?? 'target'}-${targetIndex}`}>
                    {[
                      target.path,
                      target.day == null ? null : `day ${target.day}`,
                      target.sequence == null ? null : `sequence ${target.sequence}`,
                      target.placeId,
                    ].filter(Boolean).join(' / ')}
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

function issueSummary(issue: ValidationIssue) {
  return [
    issue.code,
    issue.path,
    issue.day == null ? null : `day ${issue.day}`,
    issue.sequence == null ? null : `sequence ${issue.sequence}`,
    issue.placeId,
  ].filter(Boolean).join(' / ')
}
