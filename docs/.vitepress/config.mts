import { defineConfig } from 'vitepress'

         export default defineConfig({
                                     title : 'Lyricon Docs',
                                     description : 'Lyricon 使用与开发文档',
                                     base : '/lyricon/',
                                            cleanUrls : true,
                                            lastUpdated : true,
                                     head : [
                                     ['link', { rel: 'icon', href: '/lyricon/logo.svg' }]
                                     ],
                                     themeConfig : {
                                     logo : '/logo.svg',
                                     siteTitle : '词幕',
                                     nav : [
                                     { text : '首页', link: '/' },
                                     { text: 'App', link: '/app/' },
                                           { text : 'Developer', link: '/developer/' },
                                           { text : 'GitHub', link: 'https://github.com/tomakino/lyricon' }
                                     ],
                                     sidebar : [
                                     {
                                     text: 'App',
                                         items : [
                                         { text : '使用说明', link: '/app/' }
                                     ]
                                     },
                                     {
                                     text : 'Developer',
                                     items : [
                                     { text : '概览', link: '/developer/' }
                                     ]
                                    },
                                    {
                                    text : 'Provider',
                                    collapsed : false,
                                                items : [
                                                { text : '概览', link: '/developer/provider/' },
                                                         { text : '快速开始', link: '/developer/provider/quick-start' },
                                                         { text : 'Manifest 配置', link: '/developer/provider/manifest' },
                                                         { text : '连接生命周期', link: '/developer/provider/connection' },
                                                           { text : '播放器控制', link: '/developer/provider/player-control' },
                                                           { text : '歌词数据结构', link: '/developer/provider/lyrics-model' },
                                                                    { text : '本地测试', link: '/developer/provider/local-testing' },
                                                                    { text : '常见问题', link: '/developer/provider/faq' }
                                                                    ]
                                    },
                                    {
                                    text : 'Subscriber',
                                    collapsed: false,
                                             items : [
                                             { text: '概览', link: '/developer/subscriber/' },
                                                   { text : '快速开始', link: '/developer/subscriber/quick-start' },
                                                   { text : '连接生命周期', link: '/developer/subscriber/connection' },
                                    { text : '活跃播放器', link: '/developer/subscriber/active-player' },
                                    { text : '回调说明', link: '/developer/subscriber/callbacks' },
                                    { text: '常见问题', link: '/developer/subscriber/faq' }
                                          ]
                                          }
                                          ],
                                          socialLinks: [
                                                     { icon : 'github', link: 'https://github.com/tomakino/lyricon' }
                                                     ],
                                                     search : {
                                                     provider : 'local'
                                                     },
                                                     outline : {
                                                     level : [2, 3],
                                                                  label : '页面导航'
                                                                  },
                                                                  docFooter: {
                                                                           prev : '上一页',
                                                                           next : '下一页'
                                                                           },
                                                                            lastUpdated : {
                                                                            text : '最后更新',
                                                                            formatOptions: {
                                                                                         dateStyle : 'short',
                                                                                         timeStyle : 'medium'
                                                                                         }
                                                     },
                                                     editLink : {
                                                     pattern : 'https://github.com/tomakino/lyricon/edit/master/docs/:path',
                                                               text : '在 GitHub 上编辑此页'
                                                               },
                                                               footer : {
                                                     message : 'Released under the Apache-2.0 License.',
                                                     copyright : 'Copyright © 2026 Proify, Tomakino'
                                                     }
                                                     }
                                    })
