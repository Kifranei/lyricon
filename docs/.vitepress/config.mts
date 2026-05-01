import { defineConfig } from 'vitepress'

const zhSidebar = [
  {
    text: 'App',
    items: [{ text: '使用说明', link: '/app/' }]
  },
  {
    text: 'Developer',
    items: [{ text: '概览', link: '/developer/' }]
  },
  {
    text: 'Provider',
    items: [
      { text: '概览', link: '/developer/provider/' },
      { text: '快速开始', link: '/developer/provider/quick-start' },
      { text: 'Manifest 配置', link: '/developer/provider/manifest' },
      { text: '连接生命周期', link: '/developer/provider/connection' },
      { text: '播放器控制', link: '/developer/provider/player-control' },
      { text: '歌词数据结构', link: '/developer/provider/lyrics-model' },
      { text: '本地测试', link: '/developer/provider/local-testing' },
      { text: '常见问题', link: '/developer/provider/faq' }
    ]
  },
  {
    text: 'Subscriber',
    items: [
      { text: '概览', link: '/developer/subscriber/' },
      { text: '快速开始', link: '/developer/subscriber/quick-start' },
      { text: '连接生命周期', link: '/developer/subscriber/connection' },
      { text: '活跃播放器', link: '/developer/subscriber/active-player' },
      { text: '回调说明', link: '/developer/subscriber/callbacks' },
      { text: '常见问题', link: '/developer/subscriber/faq' }
    ]
  }
]

const enSidebar = [
  {
    text: 'App',
    items: [{ text: 'Guide', link: '/en/app/' }]
  },
  {
    text: 'Developer',
    items: [{ text: 'Overview', link: '/en/developer/' }]
  },
  {
    text: 'Provider',
    items: [
      { text: 'Overview', link: '/en/developer/provider/' },
                                                                                    { text : 'Quick Start', link: '/en/developer/provider/quick-start' },
                                                                                    { text: 'Manifest', link: '/en/developer/provider/manifest' },
                                                                                          { text : 'Connection Lifecycle', link: '/en/developer/provider/connection' },
                                                                                          { text : 'Player Control', link: '/en/developer/provider/player-control' },
               { text : 'Lyric Model', link: '/en/developer/provider/lyrics-model' },
               { text : 'Local Testing', link: '/en/developer/provider/local-testing' },
               { text: 'FAQ', link: '/en/developer/provider/faq' }
    ]
  },
  {
    text: 'Subscriber',
    items: [
      { text: 'Overview', link: '/en/developer/subscriber/' },
{ text : 'Quick Start', link: '/en/developer/subscriber/quick-start' },
{ text : 'Connection Lifecycle', link: '/en/developer/subscriber/connection' },
{ text: 'Active Player', link: '/en/developer/subscriber/active-player' },
      { text : 'Callbacks', link: '/en/developer/subscriber/callbacks' },
      { text : 'FAQ', link: '/en/developer/subscriber/faq' }
    ]
  }
]

export default defineConfig({
  title: '词幕',
  description: 'Android 状态栏歌词工具',
  base: '/lyricon/',
  cleanUrls: true,
  lastUpdated: true,
  head: [['link', { rel: 'icon', href: '/lyricon/logo.svg' }]],
  themeConfig: {
    logo: '/logo.svg',
    siteTitle: '词幕',
    nav: [
      { text: '首页', link: '/' },
      { text: 'App', link: '/app/' },
      { text: 'Developer', link: '/developer/' }
    ],
    sidebar: zhSidebar,
    socialLinks: [{ icon: 'github', link: 'https://github.com/tomakino/lyricon' }],
    search: { provider: 'local' },
    outline: { level: [2, 3], label: '页面导航' },
    docFooter: { prev: '上一页', next: '下一页' },
    lastUpdated: { text: '最后更新' },
    editLink: {
      pattern: 'https://github.com/tomakino/lyricon/edit/master/docs/:path',
      text: '在 GitHub 上编辑此页'
    },
    footer: {
      message: 'Released under the Apache-2.0 License.',
      copyright: 'Copyright © 2026 Proify, Tomakino'
    }
  },
  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN'
    },
    en: {
      label: 'English',
      lang: 'en-US',
      title: 'Lyricon',
      description: 'Android status bar lyrics tool',
      themeConfig: {
        siteTitle: 'Lyricon',
        nav: [
          { text: 'Home', link: '/en/' },
          { text: 'App', link: '/en/app/' },
          { text: 'Developer', link: '/en/developer/' }
        ],
        sidebar: enSidebar,
        outline: { level: [2, 3], label: 'On This Page' },
        docFooter: { prev: 'Previous', next: 'Next' },
        lastUpdated: { text: 'Last updated' },
        editLink: {
          pattern: 'https://github.com/tomakino/lyricon/edit/master/docs/:path',
          text: 'Edit this page on GitHub'
        }
      }
    }
  }
})
