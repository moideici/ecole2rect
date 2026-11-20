#!/usr/bin/env node
/**
 * Parité des moyennes entre le site (ecole2recte) et l'application (ce dépôt).
 *
 * Le calcul de référence est celui du site : `packages/protocol/src/insights.ts` et `grade-trends.ts`, plus la
 * réunion « matières publiées + matières seulement notées » de la page Notes (`apps/web/src/routes/notes`).
 * Ce script fait tourner CE code sur des jeux de notes (cas écrits à la main, puis tirés au hasard avec une
 * graine fixe), et écrit les entrées et les résultats attendus dans
 * `app/src/test/resources/parite/moyennes.json`. `AveragesParityTest` rejoue les mêmes entrées avec le code
 * Kotlin (`Averages`) et exige les mêmes chiffres, au millionième près, et les mêmes arrondis d'affichage.
 *
 * Usage (depuis la racine de ce dépôt) :
 *   pnpm --dir ../ecole2recte --filter @ecole2recte/protocol build
 *   node tools/parite-moyennes/generer.mjs [chemin/vers/ecole2recte]
 */
import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const site = resolve(process.argv[2] ?? resolve(here, '../../../ecole2recte'))
const protocol = await import(pathToFileURL(resolve(site, 'packages/protocol/dist/index.js')).href)
const { generalAverage, reconcileSubjectAverages, averageOfSubjects, averageSeries, simulate, requiredGrade, weightedAverage, gradesFor } = protocol

// ——————————————————————————————————————————— hasard reproductible

let seed = 20261120
function random() {
  // mulberry32 : assez pour varier les cas, et le même fichier à chaque exécution.
  seed |= 0
  seed = (seed + 0x6d2b79f5) | 0
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
const pick = (items) => items[Math.floor(random() * items.length)]
const chance = (p) => random() < p

// ——————————————————————————————————————————— le code de la page Notes du site

/** `matiereImplicite` + `toutes` de apps/web/src/routes/notes/+page.server.ts. */
function subjectsOfPeriod(subjectAverages, grades, code) {
  const notes = grades.filter((note) => note.periodCode === code)
  const publiees = subjectAverages.filter((entry) => entry.periodCode === code)
  const connues = new Set(publiees.map((entry) => entry.subject))
  const implicites = [...new Set(notes.map((note) => note.subject))]
    .filter((subject) => subject !== '' && !connues.has(subject))
    .map((subject) => ({ periodCode: code, subject, teacher: '', teachers: [], appreciation: '', parentSubject: '' }))
  return [...publiees, ...implicites]
}

const fixed = (value) => (value === undefined || value === null ? null : value.toFixed(2).replace('.', ','))
const num = (value) => (value === undefined ? null : value)

function expected(input) {
  const out = { periods: [] }
  for (const period of input.periods) {
    const subjects = subjectsOfPeriod(input.subjectAverages, input.grades, period.code)
    const verdicts = reconcileSubjectAverages(subjects, input.grades, period.code)
    const general = generalAverage(period.average, input.grades, period.code, subjects)
    const notes = input.grades.filter((note) => note.periodCode === period.code)
    const first = subjects[0]?.subject
    const target = 12 + Math.floor(random() * 8)
    const hypothesis = Math.round(random() * 40) / 2
    out.periods.push({
      code: period.code,
      subjects: subjects.map((entry) => entry.subject),
      verdicts: verdicts.map(({ average, verdict }) => ({
        subject: average.subject,
        value: num(verdict.value),
        display: fixed(verdict.value),
        origin: verdict.origin,
        official: num(verdict.official),
        behind: verdict.behind.map((grade) => grade.id),
      })),
      averageOfSubjects: num(averageOfSubjects(verdicts)),
      flat: num(weightedAverage(gradesFor(input.grades, period.code))),
      general: {
        value: num(general.value),
        display: fixed(general.value),
        origin: general.origin,
        official: num(general.official),
        behind: general.behind.map((grade) => grade.id),
      },
      generalSeries: averageSeries(notes, period.code, subjects),
      subjectSeries: first === undefined ? [] : averageSeries(notes, period.code, subjects, first),
      simulation:
        first === undefined
          ? null
          : (() => {
              const result = simulate(input.grades, period.code, { subject: first, value: hypothesis })
              const vise = requiredGrade(input.grades, period.code, { subject: first, target })
              return {
                subject: first,
                value: hypothesis,
                target,
                before: num(result.before),
                after: num(result.after),
                delta: num(result.delta),
                required: vise ? vise.value : null,
                reachable: vise ? vise.reachable : null,
              }
            })(),
    })
  }
  return out
}

// ——————————————————————————————————————————— les cas

function grade(id, subject, value, options = {}) {
  const entry = {
    id: String(id),
    subject,
    label: `Devoir ${id}`,
    scale: options.scale ?? 20,
    coefficient: options.coefficient ?? 1,
    periodCode: options.period ?? 'A001',
    counted: options.counted ?? true,
    comment: '',
    kind: '',
    skills: [],
  }
  if (value !== undefined && value !== null) entry.value = value
  if (options.date !== null) entry.date = options.date ?? '2026-10-01'
  return entry
}

function subject(name, average, options = {}) {
  const entry = { periodCode: options.period ?? 'A001', subject: name, teacher: '', teachers: [], appreciation: '', parentSubject: options.parent ?? '' }
  if (average !== undefined && average !== null) entry.average = average
  if (options.coefficient !== undefined) entry.coefficient = options.coefficient
  return entry
}

const handWritten = [
  {
    name: 'notes seules : barèmes, coefficients, Abs, non significative',
    periods: [{ code: 'A001' }],
    grades: [
      grade(1, 'Maths', 16, { coefficient: 2, date: '2026-10-03' }),
      grade(2, 'Maths', 5, { scale: 10, date: '2026-10-10' }),
      grade(3, 'Maths', null, { date: '2026-10-12' }),
      grade(4, 'Maths', 20, { counted: false, date: '2026-10-13' }),
      grade(5, 'Anglais', 12, { coefficient: 0, date: '2026-10-14' }),
      grade(6, 'Anglais', 9, { scale: 0, date: '2026-10-15' }),
    ],
    subjectAverages: [],
  },
  {
    name: 'bulletin en retard d’une note dans une matière',
    periods: [{ code: 'A001', average: 10.67 }],
    grades: [
      grade(1, 'Maths', 12, { date: '2026-09-01' }),
      grade(2, 'Maths', 16, { date: '2026-09-15' }),
      grade(3, 'Anglais', 8, { date: '2026-09-10' }),
    ],
    subjectAverages: [subject('Maths', 12, { coefficient: 4 }), subject('Anglais', 8, { coefficient: 2 })],
  },
  {
    name: 'générale officielle gardée, sous-matière non comptée, matière implicite',
    periods: [{ code: 'A001', average: 13.42 }, { code: 'A002' }],
    grades: [
      grade(1, 'Maths', 12, { date: '2026-09-01' }),
      grade(2, 'Maths', 16, { date: '2026-09-15' }),
      grade(3, 'Physique', 11, { date: '2026-09-16', coefficient: 3 }),
      grade(4, 'Maths', 18, { date: '2026-12-01', period: 'A002' }),
      grade(5, 'Maths', 7, { date: null, period: 'A002' }),
    ],
    subjectAverages: [
      subject('Maths', 14, { coefficient: 4 }),
      subject('Spé maths', 18, { coefficient: 2, parent: 'Maths' }),
      subject('Anglais', 13.5, { coefficient: 0 }),
    ],
  },
  {
    name: 'écart inexpliqué et notes sans date',
    periods: [{ code: 'A001' }],
    grades: [grade(1, 'Maths', 12, { date: null }), grade(2, 'Maths', 16, { date: null }), grade(3, 'SVT', 10), grade(4, 'SVT', 15, { date: '2026-10-20' })],
    subjectAverages: [subject('Maths', 12), subject('SVT', 5, { coefficient: 1.5 })],
  },
  {
    name: 'arrondis d’affichage (toFixed)',
    periods: [{ code: 'A001' }],
    grades: [grade(1, 'Maths', 1.005, { date: '2026-10-01' }), grade(2, 'Français', 14.125), grade(3, 'Histoire', 10.555), grade(4, 'Histoire', 10.565, { coefficient: 2 })],
    subjectAverages: [],
  },
]

const SUBJECTS = ['Maths', 'Français', 'Anglais', 'Physique', 'Histoire', 'SVT']
const DATES = ['2026-09-08', '2026-09-15', '2026-09-22', '2026-10-01', '2026-10-06', '2026-10-13', '2026-11-03', '2026-11-17']

function randomCase(index) {
  const periods = [{ code: 'A001' }, { code: 'A002' }]
  const grades = []
  const count = 2 + Math.floor(random() * 16)
  for (let i = 0; i < count; i += 1) {
    const scale = chance(0.8) ? 20 : pick([10, 5, 40, 0])
    const value = chance(0.1) ? null : Math.round(random() * scale * 4) / 4
    grades.push(
      grade(`${index}-${i}`, pick(SUBJECTS), value, {
        scale,
        coefficient: chance(0.75) ? 1 : pick([2, 0.5, 3, 0, -1]),
        counted: chance(0.9),
        date: chance(0.07) ? null : pick(DATES),
        period: chance(0.8) ? 'A001' : pick(['A002', 'A003']),
      }),
    )
  }
  const subjectAverages = []
  for (const name of SUBJECTS) {
    if (chance(0.25)) continue
    for (const period of ['A001', 'A002']) {
      const own = gradesFor(grades, period, name)
      let average
      const roll = random()
      if (roll < 0.35) average = weightedAverage(own)
      else if (roll < 0.6 && own.length > 1) {
        // La moyenne d'avant la (ou les) dernière(s) note(s), arrondie comme un bulletin.
        const sorted = [...own].sort((left, right) => (right.date ?? '').localeCompare(left.date ?? ''))
        const before = weightedAverage(sorted.slice(1 + Math.floor(random() * Math.min(2, sorted.length - 1))))
        average = before === undefined ? undefined : Math.round(before * 100) / 100
      } else if (roll < 0.8) average = Math.round(random() * 2000) / 100
      if (average !== undefined) average = Math.round(average * 100) / 100
      subjectAverages.push(
        subject(name, average, {
          period,
          coefficient: chance(0.3) ? undefined : pick([1, 2, 3, 4, 0, 1.5]),
          parent: chance(0.1) ? 'Maths' : '',
        }),
      )
    }
  }
  for (const period of periods) {
    if (chance(0.5)) period.average = Math.round(random() * 2000) / 100
  }
  return { name: `hasard ${index}`, periods, grades, subjectAverages }
}

const cases = [...handWritten]
for (let index = 0; index < 80; index += 1) cases.push(randomCase(index))

const fixture = {
  source: 'ecole2recte/packages/protocol (insights.ts, grade-trends.ts) et apps/web/src/routes/notes/+page.server.ts',
  cases: cases.map((input) => ({ ...input, expected: expected(input) })),
}

const out = resolve(here, '../../app/src/test/resources/parite/moyennes.json')
mkdirSync(dirname(out), { recursive: true })
// Un cas par ligne : un diff lisible quand le calcul du site change.
const lines = fixture.cases.map((entry) => JSON.stringify(entry)).join(',\n')
writeFileSync(out, `{"source":${JSON.stringify(fixture.source)},"cases":[\n${lines}\n]}\n`)
console.log(`${fixture.cases.length} cas écrits dans ${out}`)
