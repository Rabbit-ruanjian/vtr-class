function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function normalizeMarkdown(value) {
  return String(value || '')
    .replace(/&#x9;|&#9;/gi, '\t')
    .replace(/&#x20;|&#32;|&nbsp;/gi, ' ')
    .replace(new RegExp('\\\\{1,2}[ \\t]*(?=\\r?\\n|$)', 'g'), '')
    .replace(/^\s*\\+\s*$/gm, '')
    .replace(/\\([\\`*_{}[\]()#+.!-])/g, '$1')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function renderInline(value) {
  const links = []
  const code = []
  const protectedText = String(value || '')
    .replace(/\[([^\]]+)\]\(\s*(https?:\/\/[^\s<>"'，。！？；：、)\]}]+)\s*\)/gi, (_, label, url) => {
      const index = links.length
      links.push({ label, url })
      return `@@AI_LINK_${index}@@`
    })
    .replace(/https?:\/\/[^\s<>"'，。！？；：、)\]}]+/gi, (url) => {
      const index = links.length
      links.push({ label: url, url })
      return `@@AI_LINK_${index}@@`
    })
    .replace(/`([^`\n]+)`/g, (_, content) => {
      const index = code.length
      code.push(content)
      return `@@AI_CODE_${index}@@`
    })

  return escapeHtml(protectedText)
    .replace(/@@AI_LINK_(\d+)@@/g, (_, index) => {
      const link = links[Number(index)]
      return `<a href="${escapeHtml(link.url)}" target="_blank" rel="noreferrer">${escapeHtml(link.label)}</a>`
    })
    .replace(/@@AI_CODE_(\d+)@@/g, (_, index) => `<code>${escapeHtml(code[Number(index)])}</code>`)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.+?)__/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
    .replace(/(^|[^_])_([^_\n]+)_(?!_)/g, '$1<em>$2</em>')
}

export function renderAiMarkdown(value) {
  const lines = normalizeMarkdown(value).split(/\r?\n/)
  const blocks = []
  let paragraph = []
  let listType = null
  let listItems = []
  let codeLines = null

  const listMatch = (line) => {
    const unordered = line.match(/^\s*[-*+]\s+(.+)$/)
    const ordered = line.match(/^\s*\d+[.)]\s+(.+)$/)
    return unordered || ordered
      ? { type: unordered ? 'ul' : 'ol', content: (unordered || ordered)[1] }
      : null
  }

  // AI 经常把“编号标题”和它的解释段落分开写：
  // 1. 标题\n\n解释\n\n2. 标题。将中间的解释并入上一项，
  // 这样浏览器只会生成一个连续的 <ol>，编号就不会反复从 1 开始。
  const hasFutureListItem = (startIndex, expectedType) => {
    for (let index = startIndex + 1; index < lines.length; index += 1) {
      const candidate = lines[index]
      if (!candidate.trim()) continue
      if (/^\s*```/.test(candidate) || /^\s*#{1,3}\s+/.test(candidate)) return false
      const match = listMatch(candidate)
      if (match) return match.type === expectedType
    }
    return false
  }

  const flushParagraph = () => {
    if (!paragraph.length) return
    blocks.push(`<p>${paragraph.map(renderInline).join('<br>')}</p>`)
    paragraph = []
  }
  const flushList = () => {
    if (!listItems.length) return
    blocks.push(`<${listType}>${listItems.map((item) => `<li>${renderInline(item).replace(/\r?\n/g, '<br>')}</li>`).join('')}</${listType}>`)
    listType = null
    listItems = []
  }
  const flushCode = () => {
    if (codeLines === null) return
    blocks.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`)
    codeLines = null
  }

  for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
    const line = lines[lineIndex]
    if (/^\s*```/.test(line)) {
      flushParagraph()
      flushList()
      if (codeLines === null) codeLines = []
      else flushCode()
      continue
    }
    if (codeLines !== null) {
      codeLines.push(line)
      continue
    }
    if (!line.trim()) {
      flushParagraph()
      // 允许列表项之间存在空行，等看到下一段内容后再决定是否结束列表。
      continue
    }
    const heading = line.match(/^\s*(#{1,3})\s+(.+)$/)
    if (heading) {
      flushParagraph()
      flushList()
      const level = heading[1].length
      blocks.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
      continue
    }
    const listItem = listMatch(line)
    if (listItem) {
      flushParagraph()
      if (listType && listType !== listItem.type) flushList()
      listType = listItem.type
      listItems.push(listItem.content)
      continue
    }

    if (listType && hasFutureListItem(lineIndex, listType)) {
      listItems[listItems.length - 1] += `\n${line}`
      continue
    }

    flushList()
    paragraph.push(line)
  }
  flushParagraph()
  flushList()
  flushCode()
  return blocks.join('') || '<p></p>'
}
