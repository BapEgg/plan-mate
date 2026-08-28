export type ValidationIssueCode =
  | 'GENERATION_ID_MISMATCH'
  | 'DRAFT_REQUIRED'
  | 'DAYS_REQUIRED'
  | 'DAY_COUNT_MISMATCH'
  | 'DAY_OUT_OF_RANGE'
  | 'DUPLICATE_DAY'
  | 'DAY_ITEMS_REQUIRED'
  | 'ITEM_REQUIRED'
  | 'INVALID_SEQUENCE'
  | 'DUPLICATE_SEQUENCE'
  | 'PLACE_ID_REQUIRED'
  | 'INVALID_START_TIME'
  | 'INVALID_DURATION'
  | 'CANDIDATE_NOT_ALLOWED'
  | 'REQUIRED_PLACE_MISSING'
  | 'ITEM_TIME_OVERLAP'
  | 'OUTSIDE_DAILY_WINDOW'
  | 'ITEM_CROSSES_DAY_BOUNDARY'
  | 'REPEATED_PLACE'
  | 'AVOID_CONDITION_VIOLATED'
  | 'AVOID_CONDITION_NOT_VERIFIED'
  | 'ITEM_SEQUENCE_TIME_ORDER_INVALID'
  | 'INSUFFICIENT_TRAVEL_TIME'
  | 'ADJACENT_ROUTE_NOT_FOUND'
  | 'ADJACENT_ROUTE_NOT_VERIFIED'

export type ValidationTarget = {
  path?: string
  day?: number
  sequence?: number
  placeId?: string
}

export type ValidationIssue = ValidationTarget & {
  code: ValidationIssueCode
  message: string
  condition?: string
  relatedTargets: ValidationTarget[]
}

export type AiItineraryValidationReport = {
  errors: ValidationIssue[]
  warnings: ValidationIssue[]
  unverifiedConditions: ValidationIssue[]
}

const VALIDATION_ISSUE_CODES: Set<string> = new Set([
  'GENERATION_ID_MISMATCH',
  'DRAFT_REQUIRED',
  'DAYS_REQUIRED',
  'DAY_COUNT_MISMATCH',
  'DAY_OUT_OF_RANGE',
  'DUPLICATE_DAY',
  'DAY_ITEMS_REQUIRED',
  'ITEM_REQUIRED',
  'INVALID_SEQUENCE',
  'DUPLICATE_SEQUENCE',
  'PLACE_ID_REQUIRED',
  'INVALID_START_TIME',
  'INVALID_DURATION',
  'CANDIDATE_NOT_ALLOWED',
  'REQUIRED_PLACE_MISSING',
  'ITEM_TIME_OVERLAP',
  'OUTSIDE_DAILY_WINDOW',
  'ITEM_CROSSES_DAY_BOUNDARY',
  'REPEATED_PLACE',
  'AVOID_CONDITION_VIOLATED',
  'AVOID_CONDITION_NOT_VERIFIED',
  'ITEM_SEQUENCE_TIME_ORDER_INVALID',
  'INSUFFICIENT_TRAVEL_TIME',
  'ADJACENT_ROUTE_NOT_FOUND',
  'ADJACENT_ROUTE_NOT_VERIFIED',
])

export function parseAiItineraryValidationReport(value: unknown): AiItineraryValidationReport | undefined {
  if (!isRecord(value)) {
    return undefined
  }
  const errors = parseIssues(value.errors)
  const warnings = parseIssues(value.warnings)
  const unverifiedConditions = parseIssues(value.unverifiedConditions)
  if (!errors || !warnings || !unverifiedConditions) {
    return undefined
  }
  return {
    errors,
    warnings,
    unverifiedConditions,
  }
}

function parseIssues(value: unknown): ValidationIssue[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  const issues = value.map(parseIssue)
  return issues.every((issue): issue is ValidationIssue => Boolean(issue)) ? issues : undefined
}

function parseIssue(value: unknown): ValidationIssue | undefined {
  if (!isRecord(value) || typeof value.code !== 'string' || !VALIDATION_ISSUE_CODES.has(value.code)) {
    return undefined
  }
  if (typeof value.message !== 'string') {
    return undefined
  }
  const relatedTargets = value.relatedTargets == null ? [] : parseTargets(value.relatedTargets)
  if (!relatedTargets) {
    return undefined
  }
  return {
    code: value.code as ValidationIssueCode,
    message: value.message,
    ...(typeof value.condition === 'string' ? { condition: value.condition } : {}),
    ...parseTargetFields(value),
    relatedTargets,
  }
}

function parseTargets(value: unknown): ValidationTarget[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  const targets = value.map((target) => isRecord(target) ? parseTargetFields(target) : undefined)
  return targets.every((target): target is ValidationTarget => Boolean(target)) ? targets : undefined
}

function parseTargetFields(value: Record<string, unknown>): ValidationTarget {
  return {
    ...(typeof value.path === 'string' ? { path: value.path } : {}),
    ...(typeof value.day === 'number' ? { day: value.day } : {}),
    ...(typeof value.sequence === 'number' ? { sequence: value.sequence } : {}),
    ...(typeof value.placeId === 'string' ? { placeId: value.placeId } : {}),
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
