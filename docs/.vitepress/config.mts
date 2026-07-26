import { defineConfig } from 'vitepress'
import { defineTeekConfig } from 'vitepress-theme-teek/config'

const teekConfig = defineTeekConfig({
  teekTheme: true,
  teekHome: false,
  vpHome: true,
  loading: true,
  windowTransition: true,
  anchorScroll: true,
  sidebarTrigger: false,
  homeCardListPosition: 'right',
  pageStyle: 'default',
  themeSize: 'default',

  viewTransition: {
    enabled: true,
    mode: 'out-in',
    duration: 600,
    easing: 'ease-in'
  },

  banner: {
    enabled: true,
    name: '词幕',
    bgStyle: 'partImg',
    pureBgColor: '#28282d',
    imgSrc: ['/wallpaper.jpg'],
    imgInterval: 15000,
    imgShuffle: false,
    imgWaves: false,
    mask: true,
    maskBg: 'rgba(0, 0, 0, 0.4)',
    textColor: '#ffffff',
    descStyle: 'default',
    description: ['Android 状态栏歌词扩展工具']
  },

  themeEnhance: {
    enabled: true,
    position: 'top',
    layoutSwitch: {
      disabled: false,
      defaultMode: 'original',
      disableAnimation: false,
      defaultDocMaxWidth: 90,
      defaultPageMaxWidth: 95
    },
    themeColor: {
      disabled: false,
      defaultColorName: 'vp-default',
      defaultSpread: false,
      disabledInMobile: false
    },
    spotlight: {
      disabled: false,
      defaultStyle: 'aside',
      defaultValue: false
    }
  },

  breadcrumb: {
    enabled: true,
    showCurrentName: true,
    separator: '/',
    homeLabel: '首页'
  },

  codeBlock: {
    enabled: true,
    collapseHeight: 700,
    overlay: false,
    langTextTransform: 'uppercase'
  },

  backTop: {
    enabled: true,
    content: 'progress'
  },

  toComment: {
    enabled: false
  },

  articleShare: {
    enabled: true,
    text: '分享此页面',
    copiedText: '链接已复制',
    query: false,
    hash: false
  },

  articleBanner: {
    enabled: true,
    showCategory: false,
    showTag: false
  },

  articleAnalyze: {
    showIcon: true,
    dateFormat: 'yyyy-MM-dd',
    showInfo: true,
    showAuthor: false,
    showCreateDate: true,
    showUpdateDate: false,
    showCategory: false,
    showTag: false
  },

  docAnalysis: {
    enabled: true,
    wordCount: true,
    readingTime: true,
    statistics: {
      provider: 'busuanzi',
      siteView: true,
      pageView: true,
      permalink: true
    }
  },

  tagColor: [
    { border: '#c7d2fe', bg: '#eef2ff', text: '#4f46e5' },
    { border: '#a5f3fc', bg: '#ecfeff', text: '#0891b2' },
    { border: '#a7f3d0', bg: '#ecfdf5', text: '#059669' },
    { border: '#fde68a', bg: '#fffbeb', text: '#d97706' },
    { border: '#fbcfe8', bg: '#fdf2f8', text: '#db2777' },
    { border: '#bfdbfe', bg: '#eff6ff', text: '#2563eb' },
    { border: '#e9d5ff', bg: '#faf5ff', text: '#9333ea' }
  ],

  footerInfo: {
    theme: {
      show: true
    },
    copyright: {
      show: true,
      createYear: 2024
    }
  }
})

const zhSidebar = [
         {
         text : 'App',
                 items : [
                 { text : '使用指南', link: '/zh-cn/app/' },
                   { text : '安装与激活', link: '/zh-cn/app/installation' },
                   { text : '首次使用', link: '/zh-cn/app/first-run' },
                            { text : '歌词提供服务', link: '/zh-cn/app/providers' },
                            { text : '基本设置', link: '/zh-cn/app/basic-settings' },
                            { text : '应用样式', link: '/zh-cn/app/app-style' },
                              { text : '文字样式', link: '/zh-cn/app/text-style' },
                              { text : '图标样式', link: '/zh-cn/app/logo-style' },
                                       { text : '动画效果', link: '/zh-cn/app/animation' },
                                       { text : '翻译', link: '/zh-cn/app/translation' },
                                       { text : '视图规则', link: '/zh-cn/app/visibility-rules' },
                                         { text : '应用设置', link: '/zh-cn/app/settings' },
                                         { text : '备份与恢复', link: '/zh-cn/app/backup-restore' },
                                                  { text : 'ROM 适配', link: '/zh-cn/app/rom-notes' },
                                                  { text : '常见问题', link: '/zh-cn/app/troubleshooting' }
                                                  ]
                        },
                        {
                        text : 'Developer',
         items : [{ text : '概览', link: '/zh-cn/developer/' }]
         },
         {
         text : 'Provider',
         items : [
         { text : '概览', link: '/zh-cn/developer/provider/' },
           { text : '快速开始', link: '/zh-cn/developer/provider/quick-start' },
           { text : 'Manifest 配置', link: '/zh-cn/developer/provider/manifest' },
                    { text : '连接生命周期', link: '/zh-cn/developer/provider/connection' },
                    { text : '播放器控制', link: '/zh-cn/developer/provider/player-control' },
                    { text : '歌词数据结构', link: '/zh-cn/developer/provider/lyrics-model' },
                      { text : '本地测试', link: '/zh-cn/developer/provider/local-testing' },
                      { text : '常见问题', link: '/zh-cn/developer/provider/faq' }
                               ]
                               },
                               {
                               text : 'Subscriber',
                                      items : [
                                      { text : '概览', link: '/zh-cn/developer/subscriber/' },
                                      { text : '快速开始', link: '/zh-cn/developer/subscriber/quick-start' },
                                        { text : '连接生命周期', link: '/zh-cn/developer/subscriber/connection' },
                                        { text : '活跃播放器', link: '/zh-cn/developer/subscriber/active-player' },
                                                 { text : '回调说明', link: '/zh-cn/developer/subscriber/callbacks' },
                                                 { text : '常见问题', link: '/zh-cn/developer/subscriber/faq' }
                                                 ]
                                        }
                                        ]

                                        const enSidebar = [
                                        {
                                        text : 'App',
                                                                                                                                                     items : [
                                                                                                                                                     { text : 'Guide', link: '/en/app/' },
                                                                                                                                                       { text : 'Installation', link: '/en/app/installation' },
                                                                                                                                                       { text : 'First Run', link: '/en/app/first-run' },
                                                                                                                                                                { text : 'Lyric Providers', link: '/en/app/providers' },
                                                                                                                                                                { text : 'Basic Settings', link: '/en/app/basic-settings' },
                                                                                                                                                                { text : 'App Styles', link: '/en/app/app-style' },
                                                                                                                                                                  { text : 'Text Style', link: '/en/app/text-style' },
                                                                                                                                                                  { text : 'Logo Style', link: '/en/app/logo-style' },
                                                                                                                                                                           { text : 'Animation Effects', link: '/en/app/animation' },
                                                                                                                                                                           { text : 'Translation', link: '/en/app/translation' },
                                                                                                                                                                           { text : 'View Rules', link: '/en/app/visibility-rules' },
                                                                                                                                                                             { text : 'App Settings', link: '/en/app/settings' },
                                                                                                                                                                             { text : 'Backup And Restore', link: '/en/app/backup-restore' },
                                                                                                                                                                                      { text : 'ROM Notes', link: '/en/app/rom-notes' },
                                                                                                                                                                                      { text : 'Troubleshooting', link: '/en/app/troubleshooting' }
                                                                                                                                                                                      ]
                                                   },
                                                   {
                                                   text : 'Developer',
                                                          items : [{ text : 'Overview', link: '/en/developer/' }]
                                                          },
                                        {
                                        text : 'Provider',
                                        items : [
                                        { text : 'Overview', link: '/en/developer/provider/' },
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
                                                  items : [
                                                  { text : 'Overview', link: '/en/developer/subscriber/' },
{ text : 'Quick Start', link: '/en/developer/subscriber/quick-start' },
{ text : 'Connection Lifecycle', link: '/en/developer/subscriber/connection' },
{ text: 'Active Player', link: '/en/developer/subscriber/active-player' },
      { text : 'Callbacks', link: '/en/developer/subscriber/callbacks' },
      { text : 'FAQ', link: '/en/developer/subscriber/faq' }
]
}
]

export default defineConfig({
                            extends: teekConfig,
                            title : '词幕',
                            description : 'Android 状态栏歌词工具',
                            base : '/lyricon/',
                                   cleanUrls : true,
                                   lastUpdated : true,
                            head : [['link', { rel: 'icon', href: '/lyricon/logo.svg' }]],
                            themeConfig : {
                            logo : '/logo.svg',
                            siteTitle : '词幕',
                            nav : [
                            { text : '首页', link: '/' },
                                                                                                                                                                                                                                                                                                                                                          { text : 'App', link: '/zh-cn/app/' },
                            { text: 'Developer', link: '/zh-cn/developer/' }
                                  ],
                                  sidebar : zhSidebar,
                            socialLinks : [{ icon : 'github', link: 'https://github.com/tomakino/lyricon' }],
                            search : { provider : 'local' },
                                       outline : { level : [2, 3], label : '页面导航' },
                                                             docFooter : { prev : '上一页', next: '下一页' },
                                                                           lastUpdated : { text : '最后更新' },
                                                                           editLink: {
                                                                                   pattern : 'https://github.com/tomakino/lyricon/edit/master/docs/:path',
                                                                                   text : '在 GitHub 上编辑此页'
                                                                                   },
                                                                                    footer : {
                                                                                    message : 'Released under the Apache-2.0 License.',
                                                                                    copyright: 'Copyright © 2026 Proify, Tomakino'
                                                                                             }
                                                                                             },
                                                                                             locales : {
                                                                                   root : {
                                                                                   label : '简体中文',
                                                                                   lang : 'zh-CN'
                                                                                          },
                                                                                          en : {
                                                                                          label : 'English',
                                                                                                  lang : 'en-US',
                                                                                                  title : 'Lyricon',
                                                                                                  description : 'Android status bar lyrics tool',
                                                                                   themeConfig : {
                                                                                   siteTitle : 'Lyricon',
                                                                                   nav : [
                                                                                   { text : 'Home', link: '/en/' },
                                                                                   { text : 'App', link: '/en/app/' },
                                                                                   { text : 'Developer', link: '/en/developer/' }
                                                                                     ],
                                                                                     sidebar : enSidebar,
                                                                                   outline : { level : [2, 3], label : 'On This Page' },
                                                                                                            docFooter : { prev : 'Previous', next: 'Next' },
                                                                                                                                 lastUpdated : { text : 'Last updated' },
                                                                                                                                 editLink : {
                           pattern : 'https://github.com/tomakino/lyricon/edit/master/docs/:path',
                           text : 'Edit this page on GitHub'
                           }
                           }
                           }
                           }
                           })
