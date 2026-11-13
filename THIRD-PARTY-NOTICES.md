# Cognia 开放源代码许可

本文件随 Cognia 程序及其桌面发行版一同分发，列示 Cognia 使用、链接或随程序分发的开放源代码组件。各组件的版权归其作者所有；如组件采用双许可证或多许可证，本清单列示 Cognia 实际使用的许可证。

> 本文件由程序版本固定提供，管理员不可在系统设置中编辑或修订。升级程序时，本文件随新版本一并更新。

## 一、Cognia 自身许可

Cognia 以 MIT License 发布。Copyright (c) 2026 Cognia contributors.

## 二、后端与运行时组件

### 框架与基础设施

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| Spring Boot | 4.1.1 | Apache License 2.0 | https://spring.io/projects/spring-boot |
| Spring Framework | 7.0.x | Apache License 2.0 | https://spring.io/projects/spring-framework |
| Spring Security | 7.1.x | Apache License 2.0 | https://spring.io/projects/spring-security |
| Spring Data Redis | 由 Spring Boot 管理 | Apache License 2.0 | https://spring.io/projects/spring-data-redis |
| Spring AI | 2.0.1 | Apache License 2.0 | https://spring.io/projects/spring-ai |
| Spring MVC / Validation | 由 Spring Boot 管理 | Apache License 2.0 | https://spring.io/projects/spring-framework |
| Apache Tomcat (embedded) | 由 Spring Boot 管理 | Apache License 2.0 | https://tomcat.apache.org/ |
| Logback | 由 Spring Boot 管理 | EPL-1.0 / LGPL-2.1 | https://logback.qos.ch/ |
| SLF4J | 由 Spring Boot 管理 | MIT License | https://www.slf4j.org/ |
| Netty | 由 Spring Boot 管理 | Apache License 2.0 | https://netty.io/ |
| Lettuce | 由 Spring Boot 管理 | Apache License 2.0 | https://lettuce.io/ |
| HikariCP | 由 Spring Boot 管理 | Apache License 2.0 | https://github.com/brettwooldridge/HikariCP |
| Jackson | 由 Spring Boot 管理 | Apache License 2.0 | https://github.com/FasterXML/jackson |
| Micrometer | 由 Spring Boot 管理 | Apache License 2.0 | https://micrometer.io/ |

### 数据、认证与对象存储

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| PostgreSQL JDBC Driver | 42.7.x | BSD 2-Clause | https://jdbc.postgresql.org/ |
| MyBatis | 3.5.x | Apache License 2.0 | https://mybatis.org/mybatis-3/ |
| MyBatis-Plus | 3.5.17 | Apache License 2.0 | https://baomidou.com/ |
| JSqlParser | 由 MyBatis-Plus 管理 | Apache License 2.0 | https://github.com/JSQLParser/JSqlParser |
| Fastjson2 | 2.0.66 | Apache License 2.0 | https://github.com/alibaba/fastjson2 |
| JJWT | 0.13.0 | Apache License 2.0 | https://github.com/jwtk/jjwt |
| Apache Commons Pool | 2.13.1 | Apache License 2.0 | https://commons.apache.org/proper/commons-pool/ |
| AWS SDK for Java (S3) | 1.12.797 | Apache License 2.0 | https://aws.amazon.com/sdk-for-java/ |
| MinIO Java SDK | 8.5.12 | Apache License 2.0 | https://github.com/minio/minio-java |
| OkHttp | 4.12.x | Apache License 2.0 | https://square.github.io/okhttp/ |
| Okio | 3.6.x | Apache License 2.0 | https://square.github.io/okio/ |
| Guava | 33.x | Apache License 2.0 | https://github.com/google/guava |
| Bouncy Castle | 由 MinIO SDK 管理 | Bouncy Castle License (MIT-style) | https://www.bouncycastle.org/ |
| Snappy Java | 由 MinIO SDK 管理 | Apache License 2.0 | https://github.com/xerial/snappy-java |
| Apache Commons Compress / IO / Codec | 由 MinIO SDK 管理 | Apache License 2.0 | https://commons.apache.org/ |
| Lombok | 由 Spring Boot 管理 | MIT License | https://projectlombok.org/ |

### 文档解析

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| Apache PDFBox | 2.0.31 | Apache License 2.0 | https://pdfbox.apache.org/ |
| Apache POI | 5.2.5 | Apache License 2.0 | https://poi.apache.org/ |
| Apache XMLBeans | 由 Apache POI 管理 | Apache License 2.0 | https://xmlbeans.apache.org/ |
| curvesapi | 由 Apache POI 管理 | BSD 3-Clause | https://github.com/drawcurves/curvesapi |

## 三、前端与桌面端组件

### 核心框架

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| Vue.js | 3.5.x | MIT License | https://vuejs.org/ |
| Vue Router | 5.4.x | MIT License | https://router.vuejs.org/ |
| Pinia | 4.1.x | MIT License | https://pinia.vuejs.org/ |
| Vue I18n | 11.4.x | MIT License | https://vue-i18n.intlify.dev/ |
| Vite | 8.3.x | MIT License | https://vite.dev/ |
| TypeScript | 5.9.x | Apache License 2.0 | https://www.typescriptlang.org/ |
| Electron | 45.0.0 | MIT License | https://www.electronjs.org/ |
| electron-builder | 26.18.x | MIT License | https://www.electron.build/ |
| vue-tsc | 2.2.x | MIT License | https://github.com/vuejs/language-tools |
| vite-plugin-electron | 1.1.x | MIT License | https://github.com/electron-vite/vite-plugin-electron |
| @vitejs/plugin-vue | 6.0.x | MIT License | https://github.com/vitejs/vite-plugin-vue |

### 界面与交互

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| Tailwind CSS | 4.3.x | MIT License | https://tailwindcss.com/ |
| Reka UI | 2.11.x | MIT License | https://reka-ui.com/ |
| @tabler/icons-vue | 3.49.x | MIT License | https://tabler.io/icons |
| @lucide/vue | 1.59.x | ISC License | https://lucide.dev/ |
| class-variance-authority | 0.7.1 | Apache License 2.0 | https://cva.style/ |
| embla-carousel-vue | 8.6.x | MIT License | https://www.embla-carousel.com/ |
| media-chrome | 4.19.x | MIT License | https://media-chrome.org/ |
| motion-v | 2.8.x | MIT License | https://motion.dev/ |
| vue-sonner | 2.0.x | MIT License | https://github.com/wobsoriano/vue-sonner |
| vue-stick-to-bottom | 1.0.x | MIT License | https://github.com/wobsoriano/vue-stick-to-bottom |
| ansi-to-vue3 | 0.1.x | MIT License | https://github.com/iamkun/ansi-to-vue3 |
| @vueuse/core | 15.0.x | MIT License | https://vueuse.org/ |
| @tanstack/vue-table | 9.3.x | MIT License | https://tanstack.com/table |
| @vue-flow/core / background / controls / node-toolbar | 1.48.x / 1.3.x / 1.1.x | MIT License | https://vueflow.dev/ |
| @dnd-kit/abstract / dom | 0.5.x | MIT License | https://github.com/clauderic/dnd-kit |
| dnd-kit-vue | 0.0.2 | MIT License | https://github.com/unovue/dnd-kit-vue |
| @inspira-ui/plugins | 0.0.2 | MIT License | https://github.com/unovue/inspira-ui |
| tokenlens | 1.3.x | MIT License | https://github.com/ryoppippi/tokenlens |
| cn | 0.5.x | MIT License | https://github.com/unovue/cn |
| tw-animate-css | 1.4.x | MIT License | https://github.com/Wombosvideo/tw-animate-css |
| ogl | 1.0.11 | The Unlicense | https://github.com/oframe/ogl |
| @fontsource/jetbrains-mono | 5.3.x | SIL Open Font License 1.1 | https://fontsource.org/fonts/jetbrains-mono |
| @fontsource/noto-sans | 5.3.x | SIL Open Font License 1.1 | https://fontsource.org/fonts/noto-sans |

### 内容渲染、图表与 AI SDK

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| Shiki | 4.5.x | MIT License | https://shiki.style/ |
| KaTeX | 0.19.x | MIT License | https://katex.org/ |
| @comark/vue | 0.7.x | MIT License | https://github.com/comarkdown/comark |
| vue-stream-markdown | 2.1.x | MIT License | https://github.com/jinghaihan/vue-stream-markdown |
| @stream-markdown/code / math / mermaid | 2.1.x | MIT License | https://github.com/jinghaihan/vue-stream-markdown |
| @unovis/ts / @unovis/vue | 1.7.x | Apache License 2.0 | https://unovis.dev/ |
| ai (Vercel AI SDK) | 7.0.x | Apache License 2.0 | https://sdk.vercel.ai/ |
| zod | 4.6.x | MIT License | https://zod.dev/ |
| axios | 1.20.x | MIT License | https://axios-http.com/ |
| nanoid | 6.0.x | MIT License | https://github.com/ai/nanoid |
| @rive-app/webgl2 | 2.45.x | MIT License | https://rive.app/ |

### 随程序分发的静态库

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| JSZip | 3.10.1 | MIT License | https://stuk.github.io/jszip/ |
| docx-preview | 0.4.1 | MIT License | https://github.com/VolodymyrBaydalka/docxjs |
| qrcode.js | 1.x | MIT License | https://github.com/davidshimjs/qrcodejs |

## 四、独立部署的第三方服务

以下服务由部署者独立安装，不随 Cognia 程序二进制分发；其许可证仅用于说明部署依赖：

| 服务 | 许可证 | 项目地址 |
| --- | --- | --- |
| PostgreSQL | PostgreSQL License | https://www.postgresql.org/ |
| Redis 7 | BSD 3-Clause | https://redis.io/ |
| MinIO Server | GNU AGPL v3.0 | https://min.io/ |

## 五、许可证文本

### MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

### Apache License 2.0

Licensed under the Apache License, Version 2.0 (the "License")； you may not use this file except in compliance with the License. You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

### BSD 2-Clause License

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES； LOSS OF USE, DATA, OR PROFITS； OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

### ISC License

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

### SIL Open Font License 1.1

The fonts are licensed under the SIL Open Font License, Version 1.1. The full license text is available at https://openfontlicense.org/open-font-license-official-text/.

### The Unlicense

This is free and unencumbered software released into the public domain. Anyone is free to copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial. In jurisdictions that recognize copyright laws, the author or authors dedicate any and all copyright interest in the software to the public domain. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.

## 六、说明

1. 本清单依据项目实际依赖清单与各依赖包元数据整理，版本以随程序分发的构建为准。
2. 若某组件提供多份许可证，Cognia 选择上述许可证使用；完整版权声明与许可文本以对应上游仓库为准。
3. 本文件不构成对任何商标、专利或其他知识产权的授权。
4. 如对本清单有疑问，请通过项目仓库提交 issue。
