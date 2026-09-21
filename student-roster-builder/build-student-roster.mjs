import fs from 'node:fs/promises'
import { SpreadsheetFile, Workbook } from '@oai/artifact-tool'

const outputDir = 'E:/新的/源代码/outputs/student-roster-20260904'
await fs.mkdir(outputDir, { recursive: true })

const workbook = Workbook.create()
const sheet = workbook.worksheets.add('学生名单')
sheet.showGridLines = false

const rows = [
  ['student_number', 'name'],
  ['2026S001', '张子轩'],
  ['2026S002', '李欣怡'],
  ['2026S003', '王浩然'],
  ['2026S004', '刘诗涵'],
  ['2026S005', '陈宇航'],
  ['2026S006', '杨思远'],
  ['2026S007', '赵雨桐'],
  ['2026S008', '黄俊杰'],
  ['2026S009', '周语嫣'],
  ['2026S010', '吴嘉豪'],
  ['2026S011', '徐梦瑶'],
  ['2026S012', '孙博文'],
  ['2026S013', '胡可欣'],
  ['2026S014', '朱明轩'],
  ['2026S015', '高梓涵'],
  ['2026S016', '林俊熙'],
  ['2026S017', '何佳宁'],
  ['2026S018', '郭子睿'],
  ['2026S019', '马艺菲'],
  ['2026S020', '罗天佑'],
  ['2026S021', '梁文博'],
  ['2026S022', '宋雨欣'],
  ['2026S023', '郑凯文'],
  ['2026S024', '谢安琪'],
  ['2026S025', '唐浩宇'],
  ['2026S026', '韩若曦'],
  ['2026S027', '冯俊逸'],
  ['2026S028', '袁心怡'],
  ['2026S029', '邓嘉诚'],
  ['2026S030', '彭思妍'],
]

sheet.getRange('A1:B31').values = rows
sheet.getRange('A1:B1').format = {
  fill: '#2563EB',
  font: { bold: true, color: '#FFFFFF' },
  horizontalAlignment: 'center',
  verticalAlignment: 'center',
}
sheet.getRange('A2:A31').format.numberFormat = '@'
sheet.getRange('A2:B31').format = {
  verticalAlignment: 'center',
  borders: { preset: 'insideHorizontal', style: 'thin', color: '#E5E7EB' },
}
sheet.getRange('A1:B31').format.borders = { preset: 'outside', style: 'thin', color: '#CBD5E1' }
sheet.getRange('A:A').format.columnWidth = 20
sheet.getRange('B:B').format.columnWidth = 14
sheet.getRange('1:1').format.rowHeight = 24
sheet.freezePanes.freezeRows(1)
sheet.tables.add('A1:B31', true, 'StudentRosterTable')

const check = await workbook.inspect({
  kind: 'table',
  range: '学生名单!A1:B8',
  include: 'values,formulas',
  tableMaxRows: 8,
  tableMaxCols: 2,
})
console.log(check.ndjson)

const preview = await workbook.render({ sheetName: '学生名单', range: 'A1:B15', scale: 1.5, format: 'png' })
await fs.writeFile(`${outputDir}/student-roster-preview.png`, new Uint8Array(await preview.arrayBuffer()))

const output = await SpreadsheetFile.exportXlsx(workbook)
await output.save(`${outputDir}/student-roster.xlsx`)
console.log(`saved ${outputDir}/student-roster.xlsx`)
