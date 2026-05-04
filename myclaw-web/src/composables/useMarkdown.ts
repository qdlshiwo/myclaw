import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

marked.setOptions({
  gfm: true,
  breaks: true,
})

function highlightCodeBlocks(html: string): string {
  const temp = document.createElement('div')
  temp.innerHTML = html
  temp.querySelectorAll('pre code').forEach((block) => {
    const el = block as HTMLElement
    const langClass = Array.from(el.classList).find((c) => c.startsWith('language-'))
    const lang = langClass ? langClass.replace('language-', '') : ''
    if (lang && hljs.getLanguage(lang)) {
      el.innerHTML = hljs.highlight(el.innerText, { language: lang }).value
    } else {
      el.innerHTML = hljs.highlightAuto(el.innerText).value
    }
  })
  return temp.innerHTML
}

export function useMarkdown(text: string) {
  return computed(() => {
    const raw = marked.parse(text || '') as string
    try {
      return highlightCodeBlocks(raw)
    } catch {
      return raw
    }
  })
}
