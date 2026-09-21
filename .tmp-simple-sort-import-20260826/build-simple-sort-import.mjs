import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "E:/新的/源代码/outputs/01a03b93-aeee-7240-9047-af36b34f42d5";
const outputPath = `${outputDir}/简单排序题库批量导入_10题.xlsx`;

const headers = [
  "题目标题",
  "题干",
  "题型",
  "选项",
  "参考答案",
  "解析",
  "难度",
  "知识点",
  "来源说明",
  "来源网址",
];

const princetonUrl = "https://algs4.cs.princeton.edu/21elementary/";
const mitUrl = "https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-fall-2011/";
const rows = [
  [
    "插入排序的有序区间",
    "对序列执行直接插入排序时，在处理第 i 个元素之前，通常可以认为哪一部分已经有序？",
    "SINGLE_CHOICE",
    "A. 当前位置右侧的全部元素\nB. 下标 0 到 i-1 的元素\nC. 整个序列\nD. 只有第 i 个元素",
    "B",
    "直接插入排序维护左侧已排序子序列，每轮把当前元素插入该子序列的合适位置。",
    "简单",
    "直接插入排序",
    "根据 Princeton Algorithms 的初等排序章节整理，题目为原创表述。",
    princetonUrl,
  ],
  [
    "冒泡排序的交换方式",
    "冒泡排序在一趟扫描中，主要通过比较并交换哪一类元素来调整顺序？",
    "SINGLE_CHOICE",
    "A. 首尾两个元素\nB. 相邻两个元素\nC. 任意间隔相同的元素\nD. 随机选取的两个元素",
    "B",
    "冒泡排序反复比较相邻元素；若顺序错误则交换，使较大元素逐步向后移动。",
    "简单",
    "冒泡排序",
    "根据 Princeton Algorithms 的初等排序章节整理，题目为原创表述。",
    princetonUrl,
  ],
  [
    "选择排序的选择对象",
    "对 n 个元素进行升序选择排序时，第 1 轮通常从未排序区间中选择什么元素放到最前面？",
    "SINGLE_CHOICE",
    "A. 最大元素\nB. 中位数\nC. 最小元素\nD. 最后一个元素",
    "C",
    "升序选择排序每轮在未排序部分找出最小元素，与未排序部分的首元素交换。",
    "简单",
    "选择排序",
    "根据 Princeton Algorithms 的初等排序章节整理，题目为原创表述。",
    princetonUrl,
  ],
  [
    "简单排序的稳定性",
    "下列排序算法中，通常属于稳定排序的是哪些？",
    "MULTIPLE_CHOICE",
    "A. 冒泡排序\nB. 直接插入排序\nC. 选择排序\nD. 堆排序",
    "A,B",
    "在通常实现中，冒泡排序和直接插入排序不会改变相等关键字的相对次序；选择排序可能因远距离交换而失稳。",
    "中等",
    "排序稳定性",
    "根据 MIT 6.006 与 Princeton Algorithms 的排序基础概念整理，题目为原创表述。",
    mitUrl,
  ],
  [
    "近乎有序序列的选择",
    "若待排序序列已经接近升序，以下哪种简单排序通常更适合优先考虑？",
    "SINGLE_CHOICE",
    "A. 选择排序\nB. 直接插入排序\nC. 随机排序\nD. 归并排序",
    "B",
    "直接插入排序在序列近乎有序时移动次数较少，最佳情况下的比较次数为 n-1。",
    "中等",
    "直接插入排序",
    "根据 Princeton Algorithms 的初等排序章节整理，题目为原创表述。",
    princetonUrl,
  ],
  [
    "插入排序的一步执行",
    "序列 [3, 1, 2] 已将第 1 个元素视为有序。执行直接插入排序的第 1 轮（插入元素 1）后，序列变为？",
    "SINGLE_CHOICE",
    "A. [1, 3, 2]\nB. [3, 1, 2]\nC. [1, 2, 3]\nD. [2, 1, 3]",
    "A",
    "把 1 插入左侧有序子序列 [3]，得到 [1, 3, 2]；元素 2 尚未处理。",
    "中等",
    "直接插入排序",
    "根据 Princeton Algorithms 的初等排序章节整理，题目为原创表述。",
    princetonUrl,
  ],
  [
    "插入排序的最佳比较次数",
    "对于已经升序排列的 n 个元素，直接插入排序在通常实现下的关键字比较次数约为？",
    "SINGLE_CHOICE",
    "A. 0\nB. n - 1\nC. n²\nD. n!",
    "B",
    "每一轮只需把当前元素与前一个元素比较一次即可确认其位置，因此总比较次数为 n-1。",
    "中等",
    "时间复杂度",
    "根据 Princeton Algorithms 的初等排序章节整理，题目为原创表述。",
    princetonUrl,
  ],
  [
    "冒泡排序的稳定性判断",
    "判断题：在通常实现中，冒泡排序只在相邻元素逆序时交换，因此能保持相等元素原有的相对次序。",
    "JUDGMENT",
    "",
    "正确",
    "相等元素不会因为“逆序”条件而互换位置，所以常规冒泡排序是稳定的。",
    "简单",
    "排序稳定性",
    "根据 Princeton Algorithms 的初等排序章节整理，题目为原创表述。",
    princetonUrl,
  ],
  [
    "选择排序的交换上界",
    "填空题：对 n 个元素进行常规选择排序，最多会进行 ______ 次元素交换。",
    "FILL",
    "",
    "n - 1",
    "选择排序每轮至多交换一次，共有 n-1 轮，因此交换次数最多为 n-1。",
    "中等",
    "选择排序",
    "根据 Princeton Algorithms 的初等排序章节整理，题目为原创表述。",
    princetonUrl,
  ],
  [
    "选择排序的稳定性原因",
    "简答题：为什么常规选择排序通常不是稳定排序？请用一句话说明原因。",
    "TEXT",
    "",
    "选择最小元素时可能与前面元素远距离交换，从而改变相等元素的相对次序。",
    "常规选择排序的远距离交换可能让两个相等关键字的相对位置发生变化，因此通常不稳定。",
    "中等",
    "排序稳定性",
    "根据 MIT 6.006 与 Princeton Algorithms 的排序基础概念整理，题目为原创表述。",
    mitUrl,
  ],
];

const workbook = Workbook.create();
const sheet = workbook.worksheets.add("简单排序题目导入");
sheet.showGridLines = false;
sheet.getRange("A1:J11").values = [headers, ...rows];
sheet.freezePanes.freezeRows(1);

const headerRange = sheet.getRange("A1:J1");
headerRange.format = {
  fill: "#1F4E78",
  font: { bold: true, color: "#FFFFFF", size: 11 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "outside", style: "thin", color: "#163A5C" },
};
headerRange.format.rowHeight = 28;

const bodyRange = sheet.getRange("A2:J11");
bodyRange.format = {
  font: { size: 10, color: "#1F2937" },
  verticalAlignment: "top",
  wrapText: true,
  borders: { preset: "all", style: "thin", color: "#D9E2F3" },
};
sheet.getRange("A2:A11").format.font = { bold: true, color: "#1F2937", size: 10 };
sheet.getRange("C2:C11").format.horizontalAlignment = "center";
sheet.getRange("E2:E11").format.horizontalAlignment = "center";
sheet.getRange("G2:G11").format.horizontalAlignment = "center";
sheet.getRange("A2:J11").format.rowHeight = 76;

const widths = [20, 50, 16, 40, 16, 40, 10, 18, 24, 48];
for (let col = 0; col < widths.length; col += 1) {
  sheet.getRangeByIndexes(0, col, 11, 1).format.columnWidth = widths[col];
}

const table = sheet.tables.add("A1:J11", true, "SimpleSortImportTable");
table.showFilterButton = true;
table.showBandedColumns = false;

const inspection = await workbook.inspect({
  kind: "table",
  range: "简单排序题目导入!A1:J11",
  include: "values,formulas",
  tableMaxRows: 11,
  tableMaxCols: 10,
});
console.log(inspection.ndjson);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 50 },
  summary: "formula error scan",
});
console.log(errors.ndjson);

await fs.mkdir(outputDir, { recursive: true });
const preview = await workbook.render({
  sheetName: "简单排序题目导入",
  range: "A1:J11",
  scale: 1,
  format: "png",
});
await fs.writeFile(`${outputDir}/简单排序题库批量导入_10题_预览.png`, new Uint8Array(await preview.arrayBuffer()));

const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(outputPath);
console.log(`SAVED ${outputPath}`);
