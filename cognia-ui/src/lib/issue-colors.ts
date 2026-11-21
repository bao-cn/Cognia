export const ISSUE_LABEL_COLORS = [
  'slate', 'gray', 'red', 'orange', 'amber', 'green', 'teal', 'blue', 'purple', 'pink',
] as const

export type IssueLabelColor = (typeof ISSUE_LABEL_COLORS)[number]

const classes: Record<string, string> = {
  slate: 'border-slate-300 bg-slate-100 text-slate-700 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200',
  gray: 'border-gray-300 bg-gray-100 text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200',
  red: 'border-red-300 bg-red-100 text-red-700 dark:border-red-700 dark:bg-red-950 dark:text-red-300',
  orange: 'border-orange-300 bg-orange-100 text-orange-700 dark:border-orange-700 dark:bg-orange-950 dark:text-orange-300',
  amber: 'border-amber-300 bg-amber-100 text-amber-800 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-300',
  green: 'border-green-300 bg-green-100 text-green-700 dark:border-green-700 dark:bg-green-950 dark:text-green-300',
  teal: 'border-teal-300 bg-teal-100 text-teal-700 dark:border-teal-700 dark:bg-teal-950 dark:text-teal-300',
  blue: 'border-blue-300 bg-blue-100 text-blue-700 dark:border-blue-700 dark:bg-blue-950 dark:text-blue-300',
  purple: 'border-purple-300 bg-purple-100 text-purple-700 dark:border-purple-700 dark:bg-purple-950 dark:text-purple-300',
  pink: 'border-pink-300 bg-pink-100 text-pink-700 dark:border-pink-700 dark:bg-pink-950 dark:text-pink-300',
}

export function issueLabelClass(color: string) {
  return classes[color] ?? classes.slate
}

export function issueLabelDot(color: string) {
  const dots: Record<string, string> = {
    slate: 'bg-slate-500', gray: 'bg-gray-500', red: 'bg-red-500', orange: 'bg-orange-500',
    amber: 'bg-amber-500', green: 'bg-green-500', teal: 'bg-teal-500', blue: 'bg-blue-500',
    purple: 'bg-purple-500', pink: 'bg-pink-500',
  }
  return dots[color] ?? dots.slate
}
