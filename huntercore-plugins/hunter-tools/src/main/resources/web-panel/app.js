const LANG_KEY = 'huntercore.panel.language';
const BACKEND_URL_KEY = 'huntercore.panel.backendUrl';
const BACKEND_API_KEY_KEY = 'huntercore.panel.apiKey';
const SESSION_TOKEN_KEY = 'huntercore.panel.sessionToken';
const CONFIG_WORKBENCH_KEY_PREFIX = 'huntercore.panel.config.';

function detectLanguage() {
  try {
    const saved = localStorage.getItem(LANG_KEY);
    if (saved === 'zh' || saved === 'en') return saved;
  } catch {
    // Ignore storage failures and fall back to browser language.
  }
  return navigator.language?.toLowerCase().startsWith('zh') ? 'zh' : 'en';
}

function storedValue(key) {
  try {
    return localStorage.getItem(key) || '';
  } catch {
    return '';
  }
}

function storeValue(key, value) {
  try {
    if (value) localStorage.setItem(key, value);
    else localStorage.removeItem(key);
  } catch {
    // The current page still works when storage is unavailable.
  }
}

function normalizeBackendUrl(value) {
  return String(value || '').trim().replace(/\/+$/, '');
}

function panelMode() {
  const mode = document.body?.dataset?.panelMode || '';
  if (mode === 'frontend' || mode === 'backend') return mode;
  const page = window.location.pathname.split('/').pop().toLowerCase();
  return page === 'frontend.html' || window.location.protocol === 'file:' ? 'frontend' : 'backend';
}

function standaloneFrontend() {
  return panelMode() === 'frontend';
}

const state = {
  session: null,
  csrf: '',
  sessionToken: storedValue(SESSION_TOKEN_KEY),
  backendUrl: normalizeBackendUrl(storedValue(BACKEND_URL_KEY)),
  apiKey: storedValue(BACKEND_API_KEY_KEY),
  webUsers: [],
  plugins: [],
  mapUrl: '',
  refreshTimer: null,
  lang: detectLanguage(),
  lastData: null,
  page: 'map',
  mode: panelMode(),
  aiChatProfiles: [],
  aiBotAliases: [],
  chatLines: []
};

const $ = (id) => document.getElementById(id);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));
const ADMIN_PAGES = ['settings', 'access', 'ai', 'admin'];
const PAGES = ['map', 'overview', 'runtime', 'plugins', 'tools', ...ADMIN_PAGES];

const translations = {
  zh: {
    'server.loading': '正在读取服务器状态...',
    'nav.map': '地图',
    'nav.overview': '总览',
    'nav.runtime': '运行时',
    'nav.plugins': '插件',
    'nav.tools': '工具',
    'nav.settings': '设置',
    'nav.access': '权限',
    'nav.ai': 'AI',
    'nav.admin': '管理',
    'language.switch': '切换到 English',
    'session.eyebrow': '网页控制台',
    'session.guest': '访客视图',
    'login.username': '玩家名 / 面板管理员',
    'login.password': '游戏密码 / 面板密码',
    'login.action': '登录',
    'register.description': '首次进服前先在这里注册；这个密码就是游戏 /login 密码，也可用于网页登录（需开启）。',
    'register.username': '玩家名称（3-16 位）',
    'register.password': '密码',
    'register.confirm': '确认密码',
    'register.action': '注册玩家',
    'register.closed': '网页登录注册已关闭。',
    'register.done': '注册成功，现在可以用同一密码进服 /login。',
    'logout.action': '退出登录',
    'remote.description': '前后端分离模式：填写后端 URL，可选 API key。',
    'remote.required': '纯前端版本需要先填写后端 URL。',
    'remote.backendUrl': '后端 URL，例如 http://127.0.0.1:8088',
    'remote.apiKey': '可选 API key',
    'remote.connect': '连接后端',
    'remote.clear': '使用本地面板',
    'remote.connected': '已连接后端：{url}',
    'remote.local': '当前使用同源面板。',
    'remote.saved': '后端连接已保存。',
    'remote.title': '远程前端 / API',
    'remote.corsEnabled': '允许独立前端跨域',
    'remote.corsAllowOrigin': '允许来源，例如 * 或 https://panel.example.com',
    'remote.apiKeyEnabled': '允许 API key 管理',
    'remote.clearApiKey': '清空 API key',
    'metric.online': '在线',
    'metric.memory': '内存',
    'map.open': '在新标签打开地图',
    'overview.eyebrow': '实时服务器',
    'overview.title': '总览',
    'worlds.title': '世界',
    'players.title': '玩家',
    'plugins.title': '插件',
    'plugins.eyebrow': '插件工作台',
    'optimization.title': '优化',
    'runtime.eyebrow': '自适应服务器运行时',
    'runtime.title': '运行时预算',
    'optimization.mode': '线程模式',
    'players.loginRequired': '登录后查看玩家详情。',
    'plugins.loginRequired': '登录后查看插件详情。',
    'plugins.count': '{count} 个',
    'worlds.none': '暂无已加载世界。',
    'players.none': '当前没有玩家在线。',
    'tools.eyebrow': 'Minecraft 操作',
    'tools.title': '工具',
    'console.title': '命令控制台',
    'console.placeholder': 'list',
    'console.run': '运行',
    'quick.saveAll': '保存全部',
    'quick.clearWeather': '晴天',
    'quick.day': '白天',
    'command.loginRequired': '登录后运行允许的命令。',
    'command.dispatched': '命令已发送。',
    'command.loggedOut': '已退出登录。',
    'command.loginFailed': '登录失败。',
    'command.loggedIn': '已登录为 {username} ({role})。',
    'command.error': '错误：{message}',
    'actors.title': '实体',
    'actors.name': '名称',
    'actors.spawn': '生成',
    'actors.spawned': '实体已生成。',
    'actors.removed': '实体已移除。',
    'actors.none': '暂无配置实体。',
    'actors.spawnPoint': '出生点',
    'actors.npc': 'NPC',
    'actors.fakePlayer': '假人',
    'actors.realFakePlayer': '真实假人',
    'actors.villager': '村民',
    'actors.mannequin': '模型假人',
    'actors.pose': '姿态',
    'actors.loops': '循环动作',
    'actors.clickCommand': '点击指令',
    'actors.noClickCommand': '未设置点击指令',
    'actors.clickPlaceholder': '点击后执行，如 say %player%',
    'actors.saveClick': '保存点击',
    'actors.clearClick': '清空点击',
    'actors.clickSaved': '点击指令已保存。',
    'actors.aiEnabled': 'AI',
    'actors.aiPersona': 'NPC 人设',
    'actors.aiGoal': 'AI 目标',
    'actors.aiPersonaPlaceholder': '例如：主城向导，语气温和，知道服务器规则',
    'actors.aiGoalPlaceholder': '例如：寻找附近树木，挖掘木头并返回出生点',
    'actors.saveAi': '保存 AI',
    'actors.aiSaved': 'AI 设置已保存。',
    'actors.aiStatus': '最近动作',
    'actors.notConfigured': '未配置',
    'actors.live': '在线',
    'actors.configured': '已配置',
    'admin.required': '需要管理员会话。',
    'admin.eyebrow': '运维控制',
    'admin.title': '管理',
    'settings.eyebrow': '服务器展示',
    'settings.title': '设置',
    'settings.tab.web': '网页面板',
    'settings.tab.webHint': '名称、F3 和面板地址',
    'settings.tab.display': '游戏显示',
    'settings.tab.displayHint': '侧边栏与 TPS 文案',
    'settings.tab.motd': 'MOTD',
    'settings.tab.motdHint': '服务器列表展示',
    'settings.tab.messages': '命令文案',
    'settings.tab.messagesHint': '公开命令提示文字',
    'access.eyebrow': '登录与远程访问',
    'access.title': '权限',
    'access.tab.auth': 'HunterAuth',
    'access.tab.authHint': '游戏登录与网页注册',
    'access.tab.remote': '远程 API',
    'access.tab.remoteHint': '跨域和 API key',
    'access.tab.users': '网页身份',
    'access.tab.usersHint': '面板用户与命令权限',
    'modules.title': '模块',
    'commands.title': '命令开关',
    'webSettings.title': '网页面板',
    'webSettings.serverName': '服务器名称',
    'webSettings.cpuMode': '线程模式',
    'webSettings.f3ServerName': 'F3 服务器名称',
    'webSettings.bind': '绑定地址',
    'webSettings.port': '网页端口',
    'webSettings.externalUrl': '公开面板域名，例如 https://panel.example.com',
    'webSettings.mapUrl': '地图地址，例如 http://%host%:8100/',
    'webSettings.publicMap': '公开地图',
    'auth.title': 'HunterAuth',
    'auth.enabled': '登录保护',
    'auth.registrationRequired': '必须创建账号密码',
    'auth.webRegistrationRequired': '必须网页登录后才能进服',
    'auth.webRegistrationEnabled': '开放网页注册',
    'auth.webLoginEnabled': '允许玩家用游戏密码登录网页',
    'auth.guiEnabled': '登录 GUI',
    'auth.openGuiOnJoin': '进服打开登录 GUI',
    'auth.minimumPasswordLength': '最小密码长度',
    'auth.registrationUrl': '网页登录地址（踢出提示使用）',
    'webSettings.save': '保存网页设置',
    'webSettings.saved': '网页设置已保存。',
    'webSettings.restarting': '网页设置已保存，面板会切换到新地址。',
    'webSettings.threadingSaved': '线程策略已保存，核心线程参数重启后会完全生效。',
    'display.title': '显示',
    'display.tpsEnabled': 'TPS 显示',
    'display.tpsActionbar': 'Actionbar TPS',
    'display.intervalTicks': '刷新间隔 ticks',
    'display.actionbarFormat': 'Actionbar 文案模板',
    'display.sidebarEnabled': '侧边栏',
    'display.sidebarTitle': '侧边栏标题',
    'display.dirtyOnly': '仅变化时刷新',
    'display.sidebarLines': '侧边栏行文案',
    'display.placeholders': '占位符：%server%、%tps%、%mspt%、%online%、%max%、%world%、%player%、%ping%、%memory%',
    'motd.title': 'MOTD',
    'motd.enabled': 'MOTD 模块',
    'motd.line1': 'MOTD 第一行',
    'motd.line2': 'MOTD 第二行',
    'motd.maxPlayers': '显示最大人数，-1 使用默认',
    'ai.approvals': '高危动作授权',
    'ai.approvalsNone': '当前没有待授权的高危假人动作。',
    'ai.approve': '批准一次',
    'ai.deny': '拒绝',
    'commandMessages.title': '命令文案',
    'commandMessages.about': '/about',
    'commandMessages.plugins': '/plugins',
    'commandMessages.opDenied': '/op 无权限',
    'commandMessages.aboutPlaceholder': '&b"HunterCore" Server &8| &fPowered by &6HunterCore',
    'commandMessages.pluginsPlaceholder': '&6插件列表 &8| &f由管理员维护',
    'commandMessages.opDeniedPlaceholder': '&c你没有权限使用 /op。',
    'commandMessages.save': '保存命令文案',
    'commandMessages.saved': '命令文案已保存。',
    'ai.title': '原生 AI',
    'ai.eyebrow': '原生 AI 控制',
    'ai.tab.provider': '模型服务',
    'ai.tab.providerHint': '模型地址与密钥',
    'ai.tab.chatNpc': '聊天和 NPC',
    'ai.tab.chatNpcHint': '回复与 NPC 动作范围',
    'ai.tab.fakePlayers': 'PlayerBot AI',
    'ai.tab.fakePlayersHint': '移动、挖掘和放置',
    'ai.tab.test': '测试',
    'ai.tab.testHint': '发送一次性提示词',
    'ai.providerTitle': '模型服务',
    'ai.chatNpcTitle': '聊天和 NPC',
    'ai.fakePlayersTitle': '真实假人 AI',
    'ai.testTitle': '测试',
    'ai.enabled': 'AI 模块',
    'ai.baseUrl': 'OpenAI 兼容 Base URL',
    'ai.model': '模型',
    'ai.apiKey': 'API key（留空保留）',
    'ai.apiKeyEnv': 'API key 环境变量',
    'ai.clearKey': '清空密钥',
    'ai.temperature': '温度 0-2',
    'ai.maxTokens': '最大 tokens',
    'ai.timeout': '超时秒数',
    'ai.chatEnabled': '聊天栏',
    'ai.chatPrefix': '聊天触发词',
    'ai.chatCooldown': '聊天冷却秒',
    'ai.chatBroadcast': '广播回复',
    'ai.npcEnabled': 'NPC AI',
    'ai.npcActions': 'NPC 动作',
    'ai.npcCooldown': 'NPC 冷却秒',
    'ai.npcRadius': 'NPC 可见半径',
    'ai.commandWhitelist': 'NPC 命令白名单',
    'ai.fakePlayersEnabled': '真实假人 AI',
    'ai.fakePlayersInterval': '假人思考间隔秒',
    'ai.fakePlayersMaxActions': '每次最多动作',
    'ai.fakePlayersMaxMoveTicks': '最大移动 ticks',
    'ai.fakePlayersMaxActionTicks': '最大挖掘/交互 ticks',
    'ai.fakePlayersRadius': '假人感知半径',
    'ai.fakePlayersMaxPlaceDistance': '最大放置距离',
    'ai.fakePlayersMovement': '允许移动',
    'ai.fakePlayersBreaking': '允许挖掘',
    'ai.fakePlayersPlacing': '允许放置方块',
    'ai.fakePlayersInteraction': '允许交互/使用工具',
    'ai.fakePlayersChatControl': '聊天控制假人',
    'ai.fakePlayersChatPrefix': '聊天控制前缀，如 @bot',
    'ai.fakePlayersChatCooldown': '聊天控制冷却秒',
    'ai.fakePlayersChatPermissionRequired': '需要权限',
    'ai.fakePlayersChatPermission': '聊天控制权限节点',
    'ai.chatPrompt': '聊天系统 Prompt',
    'ai.npcPrompt': 'NPC 系统 Prompt',
    'ai.fakePlayersPrompt': '真实假人系统 Prompt',
    'ai.save': '保存 AI 设置',
    'ai.saved': 'AI 设置已保存。',
    'ai.keyConfigured': 'API key 已配置',
    'ai.keyMissing': 'API key 未配置',
    'ai.testPrompt': '测试提示词',
    'ai.test': '测试',
    'ai.testDone': 'AI 测试完成。',
    'webUsers.title': '网页身份',
    'webUsers.username': '用户名',
    'webUsers.password': '可选网页密码',
    'webUsers.commands': '允许命令',
    'webUsers.allowedCommands': 'list spawn 或 *',
    'webUsers.save': '保存身份',
    'webUsers.saved': '网页身份已保存。',
    'webUsers.removed': '网页身份已移除。',
    'webUsers.none': '暂无网页身份配置。',
    'webUsers.webPasswordSet': '已设置网页密码',
    'webUsers.hunterAuthOnly': '仅 HunterAuth',
    'webUsers.commandsOn': '命令开',
    'webUsers.commandsOff': '命令关',
    'admin.tab.modules': '模块',
    'admin.tab.modulesHint': '开启模块和命令',
    'admin.tab.luckHint': '权限辅助命令',
    'allowed.inherit': '继承',
    'allowed.custom': '自定义',
    'allowed.none': '无',
    'action.apply': '应用',
    'action.edit': '编辑',
    'action.remove': '移除',
    'action.enable': '启用',
    'action.disable': '停用',
    'action.reload': '重载',
    'action.update': '更新',
    'action.updated': '已更新。',
    'luck.dispatched': 'LuckPerms 命令已发送。',
    'health.label': '健康',
    'health.heap': '堆内存 {value}%',
    'health.noAlerts': '无活跃告警',
    'role.guest': '访客',
    'role.player': '玩家',
    'role.admin': '管理员',
    'status.ok': '正常',
    'status.warning': '警告',
    'status.critical': '严重',
    'status.disabled': '关闭',
    'plugin.status.enabled': '已启用',
    'plugin.status.disabled': '已停用',
    'plugin.status.installed': '已安装',
    'plugin.unknownVersion': '未知版本',
    'plugin.jarUnknown': '未定位 jar',
    'plugin.loadedRuntime': '运行中插件',
    'plugin.descriptorUnknown': '描述文件未知',
    'plugin.webControls': '网页可控',
    'plugin.protected': '受保护',
    'plugin.updatePlaceholder': 'https://example.com/{name}.jar',
    'plugin.actionCompleted': '插件操作已完成。',
    'plugin.updateCompleted': '插件更新已完成。',
    'plugin.updateUrlRequired': '错误：需要更新 URL。',
    'world.online': '{count} 在线',
    'world.meta': '{chunks} 区块 · {entities} 实体 · 时间 {time}',
    'optimization.cpuThreads': 'CPU 线程',
    'optimization.paperWorkers': 'Paper 工作线程',
    'optimization.coreWorkers': '核心后台线程',
    'optimization.nettyIoThreads': 'Netty IO',
    'optimization.forkJoinParallelism': 'ForkJoin',
    'optimization.hunterToolsWorkers': 'HunterTools 工作线程',
    'optimization.webPanelWorkers': '网页工作线程',
    'optimization.guestStatusCacheMillis': '访客缓存',
    'optimization.playerStatusCacheMillis': '玩家缓存',
    'optimization.adminStatusCacheMillis': '管理缓存',
    'optimization.aiThrottleFactor': 'AI 自适应降频',
    'optimization.fakePlayerRuntimeIntervalSeconds': '假人运行间隔',
    'optimization.pluginOperationMinIntervalMillis': '插件热操作限流',
    'optimization.experimentalRegionTickingAllowed': '实验区域并行',
    'optimization.managedThreading': '核心托管线程',
    'queues.title': '异步队列',
    'hotpaths.title': 'Tick 热点采样',
    'runtime.queueThreads': '队列线程预算',
    'runtime.polling': '前端轮询',
    'runtime.roleCache': '角色缓存',
    'runtime.throttle': '自适应 AI',
    'runtime.none': '当前没有活跃数据'
  },
  en: {
    'server.loading': 'Loading server status...',
    'nav.map': 'Map',
    'nav.overview': 'Overview',
    'nav.runtime': 'Runtime',
    'nav.plugins': 'Plugins',
    'nav.tools': 'Tools',
    'nav.settings': 'Settings',
    'nav.access': 'Access',
    'nav.ai': 'AI',
    'nav.admin': 'Admin',
    'language.switch': 'Switch to Chinese',
    'session.eyebrow': 'Web console',
    'session.guest': 'Guest view',
    'login.username': 'Player name / panel admin',
    'login.password': 'Game password / panel password',
    'login.action': 'Login',
    'register.description': 'Register before joining; this password is the in-game /login password and can also sign in here when enabled.',
    'register.username': 'Player name (3-16 chars)',
    'register.password': 'Password',
    'register.confirm': 'Confirm password',
    'register.action': 'Register player',
    'register.closed': 'Web registration is closed.',
    'register.done': 'Registered. You can now join and use the same password with /login.',
    'logout.action': 'Logout',
    'remote.description': 'Detached frontend mode: fill backend URL and optional API key.',
    'remote.required': 'The standalone frontend needs a backend URL first.',
    'remote.backendUrl': 'Backend URL, e.g. http://127.0.0.1:8088',
    'remote.apiKey': 'Optional API key',
    'remote.connect': 'Connect backend',
    'remote.clear': 'Use local panel',
    'remote.connected': 'Connected backend: {url}',
    'remote.local': 'Using the same-origin panel.',
    'remote.saved': 'Backend connection saved.',
    'remote.title': 'Remote frontend/API',
    'remote.corsEnabled': 'CORS for standalone frontend',
    'remote.corsAllowOrigin': 'Allowed origin, e.g. * or https://panel.example.com',
    'remote.apiKeyEnabled': 'API key management',
    'remote.clearApiKey': 'Clear API key',
    'metric.online': 'Online',
    'metric.memory': 'Memory',
    'map.open': 'Open map in new tab',
    'chat.eyebrow': 'Live chat',
    'chat.title': 'Server chat',
    'chat.placeholder': 'Message players...',
    'chat.send': 'Send',
    'chat.login': 'Login to send',
    'chat.empty': 'No chat yet.',
    'overview.eyebrow': 'Live server',
    'overview.title': 'Overview',
    'worlds.title': 'Worlds',
    'players.title': 'Players',
    'plugins.title': 'Plugins',
    'plugins.eyebrow': 'Plugin workspace',
    'optimization.title': 'Optimization',
    'runtime.eyebrow': 'Adaptive server runtime',
    'runtime.title': 'Runtime Budgets',
    'optimization.mode': 'Thread mode',
    'players.loginRequired': 'Login to view player detail.',
    'plugins.loginRequired': 'Login to view plugin detail.',
    'plugins.count': '{count}',
    'worlds.none': 'No worlds loaded.',
    'players.none': 'No players online.',
    'tools.eyebrow': 'Minecraft actions',
    'tools.title': 'Tools',
    'console.title': 'Command console',
    'console.placeholder': 'list',
    'console.run': 'Run',
    'quick.saveAll': 'Save all',
    'quick.clearWeather': 'Clear weather',
    'quick.day': 'Day',
    'command.loginRequired': 'Login to run allowed commands.',
    'command.dispatched': 'Command dispatched.',
    'command.loggedOut': 'Logged out.',
    'command.loginFailed': 'Login failed.',
    'command.loggedIn': 'Logged in as {username} ({role}).',
    'command.error': 'Error: {message}',
    'actors.title': 'Actors',
    'actors.name': 'Name',
    'actors.spawn': 'Spawn',
    'actors.spawned': 'Actor spawned.',
    'actors.removed': 'Actor removed.',
    'actors.none': 'No configured actors.',
    'actors.spawnPoint': 'Spawn',
    'actors.npc': 'NPC',
    'actors.fakePlayer': 'Fake player',
    'actors.realFakePlayer': 'Real fake player',
    'actors.villager': 'Villager',
    'actors.mannequin': 'Mannequin',
    'actors.pose': 'pose',
    'actors.loops': 'loops',
    'actors.clickCommand': 'click',
    'actors.noClickCommand': 'no click command',
    'actors.clickPlaceholder': 'run on click, e.g. say %player%',
    'actors.saveClick': 'Save click',
    'actors.clearClick': 'Clear click',
    'actors.clickSaved': 'Click command saved.',
    'actors.aiEnabled': 'AI',
    'actors.aiPersona': 'NPC persona',
    'actors.aiGoal': 'AI goal',
    'actors.aiPersonaPlaceholder': 'Example: a calm spawn guide who knows the server rules',
    'actors.aiGoalPlaceholder': 'Example: find nearby trees, mine logs, then return to spawn',
    'actors.saveAi': 'Save AI',
    'actors.aiSaved': 'AI settings saved.',
    'actors.aiStatus': 'last action',
    'actors.notConfigured': 'not configured',
    'actors.live': 'live',
    'actors.configured': 'configured',
    'admin.required': 'Admin session required.',
    'admin.eyebrow': 'Operator controls',
    'admin.title': 'Admin',
    'settings.eyebrow': 'Server presentation',
    'settings.title': 'Settings',
    'settings.tab.web': 'Web panel',
    'settings.tab.webHint': 'Name, F3 and panel address',
    'settings.tab.display': 'Game display',
    'settings.tab.displayHint': 'Sidebar and TPS text',
    'settings.tab.motd': 'MOTD',
    'settings.tab.motdHint': 'Server list message',
    'settings.tab.messages': 'Command text',
    'settings.tab.messagesHint': 'Public command copy',
    'access.eyebrow': 'Login and remote access',
    'access.title': 'Access',
    'access.tab.auth': 'HunterAuth',
    'access.tab.authHint': 'Game login and web registration',
    'access.tab.remote': 'Remote API',
    'access.tab.remoteHint': 'CORS and API key',
    'access.tab.users': 'Web roles',
    'access.tab.usersHint': 'Panel users and commands',
    'modules.title': 'Modules',
    'commands.title': 'Command gates',
    'webSettings.title': 'Web panel',
    'webSettings.serverName': 'Server name',
    'webSettings.cpuMode': 'Thread mode',
    'webSettings.f3ServerName': 'F3 server name',
    'webSettings.bind': 'Bind address',
    'webSettings.port': 'Web port',
    'webSettings.externalUrl': 'Public panel domain, e.g. https://panel.example.com',
    'webSettings.mapUrl': 'Map URL, for example http://%host%:8100/',
    'webSettings.publicMap': 'public map',
    'auth.title': 'HunterAuth',
    'auth.enabled': 'Login protection',
    'auth.registrationRequired': 'Require account password',
    'auth.webRegistrationRequired': 'Require web pre-registration',
    'auth.webRegistrationEnabled': 'Open web registration',
    'auth.webLoginEnabled': 'Allow players to web-login with game password',
    'auth.guiEnabled': 'Login GUI',
    'auth.openGuiOnJoin': 'Open GUI on join',
    'auth.minimumPasswordLength': 'Minimum password length',
    'auth.registrationUrl': 'Web registration URL for kick message',
    'webSettings.save': 'Save web settings',
    'webSettings.saved': 'Web settings saved.',
    'webSettings.restarting': 'Web settings saved. Panel is restarting on the new address.',
    'webSettings.threadingSaved': 'Thread policy saved. Core thread parameters fully apply after restart.',
    'display.title': 'Display',
    'display.tpsEnabled': 'TPS display',
    'display.tpsActionbar': 'Actionbar TPS',
    'display.intervalTicks': 'Refresh interval ticks',
    'display.actionbarFormat': 'Actionbar text template',
    'display.sidebarEnabled': 'Sidebar',
    'display.sidebarTitle': 'Sidebar title',
    'display.dirtyOnly': 'Dirty updates only',
    'display.sidebarLines': 'Sidebar line text',
    'display.placeholders': 'Placeholders: %server%, %tps%, %mspt%, %online%, %max%, %world%, %player%, %ping%, %memory%',
    'motd.title': 'MOTD',
    'motd.enabled': 'MOTD module',
    'motd.line1': 'MOTD line 1',
    'motd.line2': 'MOTD line 2',
    'motd.maxPlayers': 'Displayed max players, -1 uses default',
    'ai.approvals': 'High-risk action approvals',
    'ai.approvalsNone': 'There are no pending high-risk fake player actions.',
    'ai.approve': 'Approve once',
    'ai.deny': 'Deny',
    'commandMessages.title': 'Command text',
    'commandMessages.about': '/about',
    'commandMessages.plugins': '/plugins',
    'commandMessages.opDenied': '/op denied',
    'commandMessages.aboutPlaceholder': '&b"HunterCore" Server &8| &fPowered by &6HunterCore',
    'commandMessages.pluginsPlaceholder': '&6Plugin list &8| &fManaged by staff',
    'commandMessages.opDeniedPlaceholder': '&cYou do not have permission to use /op.',
    'commandMessages.save': 'Save command text',
    'commandMessages.saved': 'Command text saved.',
    'ai.title': 'Native AI',
    'ai.eyebrow': 'Native AI controls',
    'ai.tab.provider': 'Provider',
    'ai.tab.providerHint': 'Model endpoint and key',
    'ai.tab.chatNpc': 'Chat and NPC',
    'ai.tab.chatNpcHint': 'Replies and NPC action scope',
    'ai.tab.fakePlayers': 'PlayerBot AI',
    'ai.tab.fakePlayersHint': 'Movement, mining and placing',
    'ai.tab.test': 'Test',
    'ai.tab.testHint': 'Send a one-off prompt',
    'ai.providerTitle': 'Provider',
    'ai.chatNpcTitle': 'Chat and NPC',
    'ai.fakePlayersTitle': 'Real fake player AI',
    'ai.testTitle': 'Test',
    'ai.enabled': 'AI module',
    'ai.baseUrl': 'OpenAI-compatible Base URL',
    'ai.model': 'Model',
    'ai.apiKey': 'API key (blank keeps current)',
    'ai.apiKeyEnv': 'API key env var',
    'ai.clearKey': 'clear key',
    'ai.temperature': 'Temperature 0-2',
    'ai.maxTokens': 'Max tokens',
    'ai.timeout': 'Timeout seconds',
    'ai.chatEnabled': 'chat',
    'ai.chatPrefix': 'Chat trigger',
    'ai.chatCooldown': 'Chat cooldown seconds',
    'ai.chatBroadcast': 'Broadcast replies',
    'ai.npcEnabled': 'NPC AI',
    'ai.npcActions': 'NPC actions',
    'ai.npcCooldown': 'NPC cooldown seconds',
    'ai.npcRadius': 'NPC radius',
    'ai.commandWhitelist': 'NPC command whitelist',
    'ai.fakePlayersEnabled': 'Real fake player AI',
    'ai.fakePlayersInterval': 'Fake player think interval seconds',
    'ai.fakePlayersMaxActions': 'Max actions per plan',
    'ai.fakePlayersMaxMoveTicks': 'Max move ticks',
    'ai.fakePlayersMaxActionTicks': 'Max mine/use ticks',
    'ai.fakePlayersRadius': 'Fake player sensing radius',
    'ai.fakePlayersMaxPlaceDistance': 'Max place distance',
    'ai.fakePlayersMovement': 'allow movement',
    'ai.fakePlayersBreaking': 'allow breaking',
    'ai.fakePlayersPlacing': 'allow block placing',
    'ai.fakePlayersInteraction': 'allow interaction/tools',
    'ai.fakePlayersChatControl': 'chat control',
    'ai.fakePlayersChatPrefix': 'Chat control prefix, e.g. @bot',
    'ai.fakePlayersChatCooldown': 'Chat control cooldown seconds',
    'ai.fakePlayersChatPermissionRequired': 'require permission',
    'ai.fakePlayersChatPermission': 'Chat control permission node',
    'ai.chatProfiles': 'AI Chat profiles',
    'ai.addProfile': 'Add profile',
    'ai.botAliases': 'AI Bot aliases',
    'ai.addBotAlias': 'Add bot',
    'ai.profileName': 'Profile name',
    'ai.profileAliases': 'Trigger names / aliases, comma separated',
    'ai.profileFormat': 'Response format, e.g. &b%name% &8> &f%response%',
    'ai.profilePrompt': 'Profile prompt',
    'ai.botTarget': 'Fake player name',
    'ai.botAliasNames': 'Trigger names / aliases, comma separated',
    'ai.remove': 'Remove',
    'ai.chatPrompt': 'Chat system prompt',
    'ai.npcPrompt': 'NPC system prompt',
    'ai.fakePlayersPrompt': 'Real fake player system prompt',
    'ai.save': 'Save AI settings',
    'ai.saved': 'AI settings saved.',
    'ai.keyConfigured': 'API key configured',
    'ai.keyMissing': 'API key missing',
    'ai.testPrompt': 'Test prompt',
    'ai.test': 'Test',
    'ai.testDone': 'AI test completed.',
    'webUsers.title': 'Web roles',
    'webUsers.username': 'Username',
    'webUsers.password': 'Optional web password',
    'webUsers.commands': 'commands',
    'webUsers.allowedCommands': 'list spawn or *',
    'webUsers.save': 'Save role',
    'webUsers.saved': 'Web role saved.',
    'webUsers.removed': 'Web role removed.',
    'webUsers.none': 'No web roles configured.',
    'webUsers.webPasswordSet': 'web password set',
    'webUsers.hunterAuthOnly': 'HunterAuth only',
    'webUsers.commandsOn': 'commands on',
    'webUsers.commandsOff': 'commands off',
    'admin.tab.modules': 'Modules',
    'admin.tab.modulesHint': 'Enable modules and commands',
    'admin.tab.luckHint': 'Permissions helper commands',
    'allowed.inherit': 'inherit',
    'allowed.custom': 'custom',
    'allowed.none': 'none',
    'action.apply': 'Apply',
    'action.edit': 'Edit',
    'action.remove': 'Remove',
    'action.enable': 'Enable',
    'action.disable': 'Disable',
    'action.reload': 'Reload',
    'action.update': 'Update',
    'action.updated': 'Updated.',
    'luck.dispatched': 'LuckPerms command dispatched.',
    'health.label': 'Health',
    'health.heap': 'Heap {value}%',
    'health.noAlerts': 'No active alerts',
    'role.guest': 'Guest',
    'role.player': 'Player',
    'role.admin': 'Admin',
    'status.ok': 'ok',
    'status.warning': 'warning',
    'status.critical': 'critical',
    'status.disabled': 'disabled',
    'plugin.status.enabled': 'enabled',
    'plugin.status.disabled': 'disabled',
    'plugin.status.installed': 'installed',
    'plugin.unknownVersion': 'unknown version',
    'plugin.jarUnknown': 'jar not resolved',
    'plugin.loadedRuntime': 'runtime plugin',
    'plugin.descriptorUnknown': 'descriptor unknown',
    'plugin.webControls': 'web controls',
    'plugin.protected': 'protected',
    'plugin.updatePlaceholder': 'https://example.com/{name}.jar',
    'plugin.actionCompleted': 'Plugin action completed.',
    'plugin.updateCompleted': 'Plugin update completed.',
    'plugin.updateUrlRequired': 'Error: update URL is required.',
    'world.online': '{count} online',
    'world.meta': '{chunks} chunks · {entities} entities · time {time}',
    'optimization.cpuThreads': 'CPU threads',
    'optimization.paperWorkers': 'Paper workers',
    'optimization.coreWorkers': 'Core workers',
    'optimization.nettyIoThreads': 'Netty IO',
    'optimization.forkJoinParallelism': 'ForkJoin',
    'optimization.hunterToolsWorkers': 'HunterTools workers',
    'optimization.webPanelWorkers': 'Web workers',
    'optimization.guestStatusCacheMillis': 'Guest cache',
    'optimization.playerStatusCacheMillis': 'Player cache',
    'optimization.adminStatusCacheMillis': 'Admin cache',
    'optimization.aiThrottleFactor': 'AI throttle',
    'optimization.fakePlayerRuntimeIntervalSeconds': 'Fake player interval',
    'optimization.pluginOperationMinIntervalMillis': 'Plugin op limiter',
    'optimization.experimentalRegionTickingAllowed': 'Experimental region ticking',
    'optimization.managedThreading': 'Managed threading',
    'queues.title': 'Async queues',
    'hotpaths.title': 'Tick hot paths',
    'runtime.queueThreads': 'Queue thread budgets',
    'runtime.polling': 'Frontend polling',
    'runtime.roleCache': 'Role cache',
    'runtime.throttle': 'Adaptive AI',
    'runtime.none': 'No active runtime data'
  }
};

function t(key, values = {}) {
  const table = translations[state.lang] || translations.en;
  const fallback = translations.en[key] || key;
  const value = table[key] || fallback;
  return value.replace(/\{([A-Za-z0-9_]+)\}/g, (_, name) => String(values[name] ?? ''));
}

function roleLabel(role) {
  return t(`role.${role === 'admin' ? 'admin' : role === 'player' ? 'player' : 'guest'}`);
}

function statusLabel(status) {
  return t(`status.${status || 'ok'}`);
}

function pluginStatusLabel(status) {
  return t(`plugin.status.${status || 'disabled'}`);
}

const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
})[char]);

function apiUrl(path) {
  const value = String(path || '');
  if (/^https?:\/\//i.test(value)) return value;
  return state.backendUrl ? `${state.backendUrl}${value.startsWith('/') ? value : `/${value}`}` : value;
}

function assetUrl(path) {
  const cleanPath = String(path || '').startsWith('/') ? path : `/${path}`;
  return state.backendUrl ? apiUrl(cleanPath) : cleanPath.replace(/^\/+/, '');
}

function renderBackendConnection() {
  const line = $('backendLine');
  if (!line) return;
  const form = $('connectionForm');
  const clearButton = $('clearConnectionButton');
  if (standaloneFrontend()) {
    if (form) form.hidden = false;
    if (clearButton) clearButton.hidden = true;
    line.textContent = state.backendUrl
      ? t('remote.connected', { url: state.backendUrl })
      : t('remote.required');
  } else {
    if (form) form.hidden = true;
    line.textContent = state.backendUrl
      ? t('remote.connected', { url: state.backendUrl })
      : t('remote.local');
  }
  if ($('backendUrl')) $('backendUrl').value = state.backendUrl;
  if ($('backendApiKey')) $('backendApiKey').value = state.apiKey;
}

const severityClass = (value) => ['ok', 'warning', 'critical', 'disabled'].includes(value) ? value : 'ok';
const liquidGlassSelector = [
  '.pluginItem',
  '.toggleItem',
  '.primaryButton',
  '.secondaryButton',
  '.smallButton',
  '.quickRow button',
  'input',
  'select',
  'textarea',
  '.navButton',
  '.configTab',
  '.productMark',
  '.statusPill',
  '.roleBadge',
  '.stateChip'
].join(',');

let toastTimer = 0;

function showToast(message) {
  const toast = $('actionToast');
  if (!toast || !message) return;
  toast.textContent = message;
  toast.hidden = false;
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toast.hidden = true;
  }, 4200);
}

function setOutput(message, output = '', editorUrl = '') {
  $('commandResult').dataset.placeholder = 'false';
  const text = output ? `${message}\n\n${output}` : message;
  $('commandResult').innerHTML = esc(text) + (editorUrl
    ? `\n\n<a class="editorLink" href="${esc(editorUrl)}" target="_blank" rel="noreferrer">Open LuckPerms WebEditor</a>`
    : '');
  showToast(message);
}

function setCommandPlaceholder() {
  $('commandResult').dataset.placeholder = 'true';
  $('commandResult').textContent = t('command.loginRequired');
}

function dataItem(left, right = '', meta = '') {
  return `<div class="dataItem"><span>${esc(left)}${meta ? `<small>${esc(meta)}</small>` : ''}</span><strong>${esc(right)}</strong></div>`;
}

function toggleItem(left, checked, attrs = '', disabled = false) {
  return `<label class="toggleItem"><span>${esc(left)}</span><input type="checkbox" ${checked ? 'checked' : ''} ${disabled ? 'disabled' : ''} ${attrs}></label>`;
}

function translateOptions(selectId, labels) {
  const select = $(selectId);
  if (!select) return;
  Array.from(select.options).forEach((option) => {
    if (labels[option.value]) option.textContent = labels[option.value];
  });
}

function applyTranslations() {
  document.documentElement.lang = state.lang === 'zh' ? 'zh-CN' : 'en';
  document.title = state.lang === 'zh' ? 'HunterCore 面板' : 'HunterCore Panel';
  $$('[data-i18n]').forEach((element) => {
    element.textContent = t(element.dataset.i18n);
  });
  $$('[data-i18n-placeholder]').forEach((element) => {
    element.setAttribute('placeholder', t(element.dataset.i18nPlaceholder));
  });
  $$('[data-i18n-title]').forEach((element) => {
    element.setAttribute('title', t(element.dataset.i18nTitle));
    element.setAttribute('aria-label', t(element.dataset.i18nTitle));
  });
  const languageToggle = $('languageToggle');
  if (languageToggle) languageToggle.textContent = state.lang === 'zh' ? 'EN' : '中文';
  const webSettingsButton = $('webSettingsForm')?.querySelector('button[type="submit"]');
  if (webSettingsButton) webSettingsButton.textContent = t('webSettings.save');
  translateOptions('webUserRole', { player: roleLabel('player'), admin: roleLabel('admin') });
  translateOptions('webUserAllowedMode', {
    inherit: t('allowed.inherit'),
    custom: t('allowed.custom'),
    none: t('allowed.none')
  });
  translateOptions('actorModule', { npcs: t('actors.npc'), 'fake-players': t('actors.fakePlayer'), 'real-fake-players': t('actors.realFakePlayer') });
  translateOptions('actorKind', { villager: t('actors.villager'), mannequin: t('actors.mannequin') });
  const commandResult = $('commandResult');
  if (commandResult?.dataset.placeholder !== 'false') setCommandPlaceholder();
  renderBackendConnection();
  if (state.lastData?.auth) renderAuthPublic(state.lastData.auth);
}

function rerenderCachedStatus() {
  updateSessionChrome();
  if (!state.lastData) return;
  const data = state.lastData;
  renderHealth(data.health);
  renderOverview(data);
  renderActorWorlds(data.worlds);
  renderActors(data.actorDetails);
  renderOperations(data.modules);
  renderWebUsers(data.webUsers);
  renderWebSettings(data.webSettings);
  renderCommandMessages(data.commandMessages);
  renderAiApprovals(data.aiApprovals);
  renderAiSettings(data.aiSettings);
}

function setLanguage(lang) {
  state.lang = lang === 'en' ? 'en' : 'zh';
  try {
    localStorage.setItem(LANG_KEY, state.lang);
  } catch {
    // Language still changes for the current page even when storage is blocked.
  }
  applyTranslations();
  rerenderCachedStatus();
}

function pageFromLocation() {
  const value = window.location.hash.replace(/^#\/?/, '');
  return PAGES.includes(value) ? value : 'map';
}

function showPage(page, push = true) {
  const targetPage = ADMIN_PAGES.includes(page) && !state.session?.admin ? 'overview' : page;
  state.page = targetPage;
  $$('.pageView').forEach((view) => {
    const active = view.id === targetPage;
    view.hidden = !active;
    view.classList.toggle('isActive', active);
  });
  $$('.navButton[data-page-target]').forEach((button) => {
    button.classList.toggle('isActive', button.dataset.pageTarget === targetPage);
  });
  if (push && window.location.hash !== `#${targetPage}`) {
    history.pushState(null, '', `#${targetPage}`);
  }
  window.scrollTo(0, 0);
}

function actorLine(actor) {
  const location = actor.world
    ? `${actor.world} ${Number(actor.x).toFixed(1)} ${Number(actor.y).toFixed(1)} ${Number(actor.z).toFixed(1)}`
    : t('actors.notConfigured');
  const clickCommand = actor.clickCommand || '';
  const clickLine = clickCommand ? clickCommand : t('actors.noClickCommand');
  const moduleLabel = actor.module === 'npcs'
    ? t('actors.npc')
    : actor.module === 'real-fake-players' ? t('actors.realFakePlayer') : t('actors.fakePlayer');
  const stateLabel = actor.live ? t('actors.live') : t('actors.configured');
  const aiCapable = actor.module === 'npcs' || actor.module === 'real-fake-players';
  const aiMeta = aiCapable
    ? ` · ${esc(t('actors.aiEnabled'))}: ${esc(actor.aiEnabled ? statusLabel('ok') : statusLabel('disabled'))}`
    : '';
  const aiStatusMeta = actor.module === 'real-fake-players' && actor.aiStatus
    ? ` · ${esc(t('actors.aiStatus'))}: ${esc(actor.aiStatus)}`
    : '';
  const metaLine = actor.module === 'real-fake-players'
    ? `${stateLabel} · ${moduleLabel} · ${esc(actor.pose || 'survival')} · ${esc(t('actors.loops'))}: ${esc(actor.loops || 'none')} · ${esc(location)} · ${esc(t('actors.clickCommand'))}: ${esc(clickLine)}${aiMeta}${aiStatusMeta}`
    : `${stateLabel} · ${moduleLabel} · ${esc(actor.kind)} · ${esc(t('actors.pose'))}: ${esc(actor.pose || 'standing')} · ${esc(location)} · ${esc(t('actors.clickCommand'))}: ${esc(clickLine)}${aiMeta}`;
  const aiLabel = actor.module === 'real-fake-players' ? t('actors.aiGoal') : t('actors.aiPersona');
  const aiPlaceholder = actor.module === 'real-fake-players' ? t('actors.aiGoalPlaceholder') : t('actors.aiPersonaPlaceholder');
  const actorAiControls = aiCapable
    ? `<label class="checkLine actorAiToggle"><input type="checkbox" data-actor-ai-enabled="true" ${actor.aiEnabled ? 'checked' : ''}> <span>${esc(t('actors.aiEnabled'))}</span></label>
      <textarea class="actorPersonaInput" rows="2" data-actor-ai-persona="true" aria-label="${esc(aiLabel)}" placeholder="${esc(aiPlaceholder)}">${esc(actor.aiPersona || '')}</textarea>
      <button type="button" data-actor-ai-save="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">${esc(t('actors.saveAi'))}</button>`
    : '';
  return `<div class="dataItem">
    <span>${esc(actor.displayName)}<small>${metaLine}</small></span>
    <div class="actorActions">
      <input class="actorCommandInput" value="${esc(clickCommand)}" placeholder="${esc(t('actors.clickPlaceholder'))}" data-actor-command-input="true">
      <button type="button" data-actor-click-save="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">${esc(t('actors.saveClick'))}</button>
      <button type="button" data-actor-click-clear="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">${esc(t('actors.clearClick'))}</button>
      ${actorAiControls}
      <button type="button" data-actor-remove="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">${esc(t('action.remove'))}</button>
    </div>
  </div>`;
}

function allowedLine(user) {
  if (!user.allowedCommandsConfigured) return t('allowed.inherit');
  return user.allowedCommands?.length ? user.allowedCommands.join(', ') : t('allowed.none');
}

function webUserLine(user) {
  return `<div class="dataItem">
    <span>${esc(user.displayName)}<small>${esc(roleLabel(user.role))} · ${user.passwordConfigured ? t('webUsers.webPasswordSet') : t('webUsers.hunterAuthOnly')} · ${user.commandExecution ? t('webUsers.commandsOn') : t('webUsers.commandsOff')} · ${esc(allowedLine(user))}</small></span>
    <span class="userActions">
      <button type="button" data-user-edit="${esc(user.id)}">${esc(t('action.edit'))}</button>
      <button type="button" data-user-remove="${esc(user.id)}">${esc(t('action.remove'))}</button>
    </span>
  </div>`;
}

function aiApprovalLine(approval) {
  const who = approval.requestedBy ? approval.requestedBy : '--';
  return `<div class="dataItem">
    <span>${esc(approval.fakePlayerName)}<small>${esc(approval.label)} · ${esc(approval.detail)} · ${esc(who)} · ${approval.expiresInSeconds}s</small></span>
    <span class="userActions">
      <button type="button" data-ai-approval="${esc(approval.fakePlayerName)}" data-ai-action="approve">${esc(t('ai.approve'))}</button>
      <button type="button" data-ai-approval="${esc(approval.fakePlayerName)}" data-ai-action="deny">${esc(t('ai.deny'))}</button>
    </span>
  </div>`;
}

function pluginLine(plugin, admin) {
  const loaded = plugin.loaded !== false;
  const enabled = Boolean(plugin.enabled);
  const controllable = Boolean(plugin.controllable);
  const updateable = Boolean(plugin.updateable);
  const status = plugin.status || (loaded ? (enabled ? 'enabled' : 'disabled') : 'installed');
  const statusClass = status === 'enabled' ? 'ok' : status === 'installed' ? 'warning' : 'critical';
  const meta = [
    plugin.version || t('plugin.unknownVersion'),
    plugin.sourceJar || t('plugin.jarUnknown'),
    plugin.descriptor || (loaded ? t('plugin.loadedRuntime') : t('plugin.descriptorUnknown')),
    controllable ? t('plugin.webControls') : t('plugin.protected')
  ].join(' · ');
  if (!admin) {
    return dataItem(plugin.name, pluginStatusLabel(status), meta);
  }
  const controlDisabled = controllable ? '' : 'disabled';
  const reloadDisabled = controllable && loaded ? '' : 'disabled';
  const updateDisabled = updateable ? '' : 'disabled';
  return `<div class="pluginItem ${status === 'enabled' ? 'isEnabled' : status === 'installed' ? 'isInstalled' : 'isDisabled'}">
    <div class="pluginTop">
      <span>${esc(plugin.name)}<small>${esc(meta)}</small></span>
      <strong class="stateChip ${statusClass}">${esc(pluginStatusLabel(status))}</strong>
    </div>
    <div class="pluginActions">
      <button type="button" class="smallButton pluginActionPrimary" data-plugin-name="${esc(plugin.name)}" data-plugin-action="${enabled ? 'disable' : 'enable'}" ${controlDisabled}>${enabled ? esc(t('action.disable')) : esc(t('action.enable'))}</button>
      <button type="button" class="smallButton" data-plugin-name="${esc(plugin.name)}" data-plugin-action="reload" ${reloadDisabled}>${esc(t('action.reload'))}</button>
    </div>
    <form class="pluginUpdateForm" data-plugin-update="${esc(plugin.name)}">
      <input name="updateUrl" placeholder="${esc(t('plugin.updatePlaceholder', { name: plugin.name }))}" ${updateDisabled}>
      <button type="submit" class="smallButton" ${updateDisabled}>${esc(t('action.update'))}</button>
    </form>
  </div>`;
}

async function json(url, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (state.csrf) headers['X-HunterCore-CSRF'] = state.csrf;
  if (state.sessionToken) headers['X-HunterCore-Session'] = state.sessionToken;
  if (state.apiKey) headers['X-HunterCore-Api-Key'] = state.apiKey;
  if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const response = await fetch(apiUrl(url), {
    credentials: state.backendUrl ? 'omit' : 'same-origin',
    ...options,
    headers
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.error || `HTTP ${response.status}`);
  }
  return payload;
}

function renderAuthPublic(auth) {
  const form = $('registerForm');
  const toggle = $('registerToggle');
  if (!form || !toggle) return;
  const enabled = Boolean(auth?.enabled && auth?.registrationRequired && auth?.webRegistrationEnabled);
  toggle.hidden = Boolean(state.session) || !enabled;
  $('registerButton').disabled = !enabled;
  const description = form.querySelector('.subtleLine');
  if (description) {
    description.textContent = enabled
      ? t('register.description')
      : t('register.closed');
  }
}

function closeAuthModals() {
  $('authBackdrop').hidden = true;
  $('loginModal').hidden = true;
  $('registerModal').hidden = true;
}

function openAuthModal(kind) {
  if (state.session) {
    closeAuthModals();
    return;
  }
  $('authBackdrop').hidden = false;
  $('loginModal').hidden = kind !== 'login';
  $('registerModal').hidden = kind !== 'register';
  if (kind === 'register' && $('registerButton').disabled) {
    setOutput(t('register.closed'));
  }
  setTimeout(() => (kind === 'register' ? $('registerUsername') : $('username'))?.focus(), 0);
}

function setAdminVisibility(admin) {
  $$('.adminOnly').forEach((element) => {
    if (element.classList.contains('pageView')) {
      if (!admin) element.hidden = true;
      return;
    }
    element.hidden = !admin;
  });
}

function updateSessionChrome() {
  const session = state.session;
  const admin = Boolean(session?.admin);
  setAdminVisibility(admin);
  if (!admin && ADMIN_PAGES.includes(state.page)) showPage('overview');
  $('sessionToggle').hidden = Boolean(session);
  $('sessionToggle').textContent = t('login.action');
  $('registerToggle').hidden = Boolean(session) || $('registerButton')?.disabled;
  $('logoutButton').hidden = !session;
  if (session) closeAuthModals();
  $('sessionTitle').textContent = session
    ? `${session.username} · ${roleLabel(session.role)}${session.authSource ? ` · ${session.authSource}` : ''}`
    : t('session.guest');
  $('sessionBadge').textContent = session ? roleLabel(session.role) : roleLabel('guest');
  $('sessionBadge').className = `roleBadge ${admin ? 'ok' : ''}`;
}

function renderHealth(health) {
  const safe = health || { status: 'disabled', alerts: [] };
  $('healthStatus').textContent = statusLabel(safe.status || 'ok');
  $('healthStatus').className = `statusPill ${severityClass(safe.status)}`;
  $('healthList').innerHTML = safe.alerts?.length
    ? safe.alerts.map((alert) => dataItem(alert.label, alert.detail, statusLabel(alert.severity))).join('')
    : dataItem(t('health.label'), t('health.heap', { value: Number(safe.memoryUsagePercent || 0).toFixed(1) }), t('health.noAlerts'));
}

function renderHomeChat(lines = state.chatLines) {
  state.chatLines = Array.isArray(lines) ? lines : [];
  $('chatStatus').textContent = String(state.chatLines.length);
  $('homeChatForm').hidden = !state.session;
  $('homeChatInput').disabled = !state.session;
  $('homeChatInput').placeholder = state.session ? t('chat.placeholder') : t('chat.login');
  $('homeChatList').innerHTML = state.chatLines.length
    ? state.chatLines.slice(-80).map((line) => `
      <div class="chatLine" data-source="${esc(line.source || 'game')}">
        <strong>${esc(line.sender || 'server')}</strong>
        <span>${esc(line.message || '')}</span>
      </div>
    `).join('')
    : `<p class="mutedState">${esc(t('chat.empty'))}</p>`;
  const list = $('homeChatList');
  list.scrollTop = list.scrollHeight;
}

async function refreshChat() {
  if (standaloneFrontend() && !state.backendUrl) return;
  const data = await json('/api/chat');
  if (data.ok) renderHomeChat(data.lines || []);
}

function renderOverview(data) {
  $('worlds').innerHTML = (data.worlds || [])
    .map((world) => dataItem(
      world.name,
      t('world.online', { count: world.players }),
      t('world.meta', { chunks: world.loadedChunks, entities: world.entities, time: world.time })
    ))
    .join('') || `<p class="mutedState">${esc(t('worlds.none'))}</p>`;

  $('optimizationList').innerHTML = [
    dataItem(t('optimization.mode'), data.optimization.mode),
    dataItem(t('optimization.cpuThreads'), data.optimization.cpuThreads),
    dataItem(t('optimization.paperWorkers'), data.optimization.paperWorkers),
    dataItem(t('optimization.coreWorkers'), data.optimization.coreWorkers),
    dataItem(t('optimization.nettyIoThreads'), data.optimization.nettyIoThreads),
    dataItem(t('optimization.forkJoinParallelism'), data.optimization.forkJoinParallelism),
    dataItem(t('optimization.hunterToolsWorkers'), data.optimization.hunterToolsWorkers),
    dataItem(t('optimization.webPanelWorkers'), data.optimization.webPanelWorkers),
    dataItem(t('optimization.experimentalRegionTickingAllowed'), String(Boolean(data.optimization.experimentalRegionTickingAllowed))),
    dataItem(t('optimization.managedThreading'), String(Boolean(data.optimization.managedThreading)))
  ].join('');

  $('runtimeBudgetList').innerHTML = [
    dataItem(t('runtime.throttle'), data.optimization.aiThrottleFactor, `${data.optimization.fakePlayerRuntimeIntervalSeconds}s`),
    dataItem(t('runtime.roleCache'), `${data.optimization.guestStatusCacheMillis}ms / ${data.optimization.playerStatusCacheMillis}ms / ${data.optimization.adminStatusCacheMillis}ms`, 'guest / player / admin'),
    dataItem(t('optimization.pluginOperationMinIntervalMillis'), `${data.optimization.pluginOperationMinIntervalMillis}ms`, t('runtime.polling')),
    dataItem(t('optimization.hunterToolsWorkers'), data.optimization.hunterToolsWorkers, `${t('optimization.webPanelWorkers')} ${data.optimization.webPanelWorkers}`),
    dataItem(t('runtime.queueThreads'), (data.queues || []).filter((queue) => queue.active).map((queue) => `${queue.name}:${queue.maxThreads}`).join(' · ') || '--')
  ].join('');

  $('queueList').innerHTML = (data.queues || []).map((queue) => dataItem(
    queue.name,
    `${queue.activeThreads}/${queue.maxThreads} · ${queue.queued} queued`,
    `${queue.state}${queue.remainingCapacity >= 0 ? ` · cap ${queue.remainingCapacity}` : ''}`
  )).join('') || `<p class="mutedState">${esc(t('runtime.none'))}</p>`;

  $('hotPathList').innerHTML = (data.hotPaths || []).map((sample) => dataItem(
    sample.category,
    sample.detail,
    `score ${Number(sample.score || 0).toFixed(2)}`
  )).join('') || `<p class="mutedState">${esc(t('runtime.none'))}</p>`;

  $('playerList').innerHTML = data.players
    ? data.players.map((player) => dataItem(player.name, `${player.ping}ms`, player.world)).join('') || `<p class="mutedState">${esc(t('players.none'))}</p>`
    : `<p class="mutedState">${esc(t('players.loginRequired'))}</p>`;

  $('pluginList').innerHTML = data.plugins
    ? data.plugins.map((plugin) => pluginLine(plugin, Boolean(data.session?.admin))).join('')
    : `<p class="mutedState">${esc(t('plugins.loginRequired'))}</p>`;
  $('pluginList').classList.toggle('mutedState', !data.plugins);
  if ($('pluginCountBadge')) {
    $('pluginCountBadge').textContent = data.plugins ? t('plugins.count', { count: data.plugins.length }) : '--';
  }
  if (data.plugins) state.plugins = data.plugins;
}

function renderActorWorlds(worlds) {
  const selected = $('actorWorld').value;
  const names = (worlds || []).map((world) => world.name);
  $('actorWorld').innerHTML = `<option value="">${esc(t('actors.spawnPoint'))}</option>` + names.map((name) => `<option value="${esc(name)}">${esc(name)}</option>`).join('');
  if (names.includes(selected)) $('actorWorld').value = selected;
}

function renderActors(actors) {
  if (!state.session?.admin) return;
  $('actorList').classList.remove('mutedState');
  $('actorList').innerHTML = actors?.length ? actors.map(actorLine).join('') : `<p class="mutedState">${esc(t('actors.none'))}</p>`;
}

function renderOperations(modules) {
  if (!state.session?.admin) return;
  $('moduleControls').innerHTML = (modules || [])
    .map((module) => toggleItem(module.name, module.enabled, `data-module="${esc(module.name)}"`, !module.toggleable))
    .join('');
  $('commandControls').innerHTML = (modules || [])
    .filter((module) => module.commands?.length)
    .map((module) => `<div class="commandGroup"><h4>${esc(module.name)}</h4>${module.commands
      .map((command) => toggleItem(command.name, command.enabled, `data-command-module="${esc(module.name)}" data-command="${esc(command.name)}"`))
      .join('')}</div>`)
    .join('');
}

function renderWebUsers(users) {
  if (!state.session?.admin) return;
  state.webUsers = users || [];
  $('webUserList').classList.remove('mutedState');
  $('webUserList').innerHTML = state.webUsers.length ? state.webUsers.map(webUserLine).join('') : `<p class="mutedState">${esc(t('webUsers.none'))}</p>`;
}

function renderWebSettings(settings) {
  if (!state.session?.admin || !settings) return;
  if (document.activeElement && document.activeElement.closest('[data-settings-scope="web"]')) return;
  $('webServerName').value = settings.serverName || '';
  $('webCpuMode').value = settings.cpuMode || 'single-thread';
  $('webF3ServerName').value = settings.f3ServerName || '';
  $('webBindAddress').value = settings.bindAddress || '';
  $('webPort').value = settings.port || '';
  $('webExternalUrl').value = settings.externalUrl || '';
  $('webMapUrl').value = settings.mapUrl || '';
  $('webPublicMap').checked = Boolean(settings.publicMap);
  $('tpsDisplayEnabled').checked = Boolean(settings.tpsDisplayEnabled);
  $('tpsActionbar').checked = Boolean(settings.tpsActionbar);
  $('tpsIntervalTicks').value = settings.tpsIntervalTicks ?? '';
  $('tpsActionbarFormat').value = settings.tpsActionbarFormat || '';
  $('sidebarEnabled').checked = Boolean(settings.sidebarEnabled);
  $('sidebarTitle').value = settings.sidebarTitle || '';
  $('sidebarIntervalTicks').value = settings.sidebarIntervalTicks ?? '';
  $('sidebarDirtyUpdatesOnly').checked = Boolean(settings.sidebarDirtyUpdatesOnly);
  $('sidebarLines').value = (settings.sidebarLines || []).join('\n');
  $('motdEnabled').checked = Boolean(settings.motdEnabled);
  $('motdLine1').value = settings.motdLine1 || '';
  $('motdLine2').value = settings.motdLine2 || '';
  $('motdMaxPlayers').value = settings.motdMaxPlayers ?? '';
  $('authEnabled').checked = Boolean(settings.authEnabled);
  $('authRegistrationRequired').checked = Boolean(settings.authRegistrationRequired);
  $('authWebRegistrationRequired').checked = Boolean(settings.authWebRegistrationRequired);
  $('authWebRegistrationEnabled').checked = Boolean(settings.authWebRegistrationEnabled);
  $('authWebLoginEnabled').checked = Boolean(settings.authWebLoginEnabled);
  $('authGuiEnabled').checked = Boolean(settings.authGuiEnabled);
  $('authOpenGuiOnJoin').checked = Boolean(settings.authOpenGuiOnJoin);
  $('authMinimumPasswordLength').value = settings.authMinimumPasswordLength ?? '';
  $('authRegistrationUrl').value = settings.authRegistrationUrl || '';
  $('webCorsEnabled').checked = Boolean(settings.corsEnabled);
  $('webCorsAllowOrigin').value = settings.corsAllowOrigin || '*';
  $('webApiKeyEnabled').checked = Boolean(settings.apiKeyEnabled);
  $('webApiKey').placeholder = settings.apiKeyConfigured ? 'API key configured' : t('remote.apiKey');
  $('webClearApiKey').checked = false;
  $('webAddressLine').textContent = settings.address || '';
  $('webThreadingLine').textContent = `${t('webSettings.cpuMode')}: ${settings.cpuMode || 'single-thread'} · ${settings.asyncEnabled ? 'async' : 'sync'} · workers ${settings.recommendedWorkers || '--'} · F3 ${settings.f3ServerName || ''}`;
}

function renderAiApprovals(approvals) {
  if (!state.session?.admin) return;
  $('aiApprovalList').classList.remove('mutedState');
  $('aiApprovalList').innerHTML = approvals?.length
    ? approvals.map(aiApprovalLine).join('')
    : `<p class="mutedState">${esc(t('ai.approvalsNone'))}</p>`;
}

function splitAliases(value) {
  return String(value || '')
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function renderAiProfileList() {
  const profiles = state.aiChatProfiles || [];
  $('aiChatProfileList').innerHTML = profiles.length ? profiles.map((profile, index) => `
    <div class="aiConfigItem" data-ai-profile-index="${index}">
      <label class="checkLine"><input type="checkbox" data-ai-profile-field="enabled" ${profile.enabled === false ? '' : 'checked'}> <span>${esc(profile.displayName || t('ai.profileName'))}</span></label>
      <div class="formGrid">
        <input data-ai-profile-field="displayName" value="${esc(profile.displayName || '')}" placeholder="${esc(t('ai.profileName'))}">
        <input data-ai-profile-field="aliases" value="${esc((profile.aliases || []).join(', '))}" placeholder="${esc(t('ai.profileAliases'))}">
        <input data-ai-profile-field="responseFormat" value="${esc(profile.responseFormat || '&b%name% &8> &f%response%')}" placeholder="${esc(t('ai.profileFormat'))}">
        <button type="button" class="secondaryButton" data-ai-profile-remove="${index}">${esc(t('ai.remove'))}</button>
      </div>
      <textarea data-ai-profile-field="systemPrompt" rows="3" spellcheck="false" placeholder="${esc(t('ai.profilePrompt'))}">${esc(profile.systemPrompt || '')}</textarea>
    </div>
  `).join('') : `<p class="mutedState">${esc(t('actors.none'))}</p>`;
}

function renderAiBotAliasList() {
  const aliases = state.aiBotAliases || [];
  $('aiBotAliasList').innerHTML = aliases.length ? aliases.map((alias, index) => `
    <div class="aiConfigItem" data-ai-bot-index="${index}">
      <label class="checkLine"><input type="checkbox" data-ai-bot-field="enabled" ${alias.enabled === false ? '' : 'checked'}> <span>${esc(alias.target || t('ai.botTarget'))}</span></label>
      <div class="formGrid">
        <input data-ai-bot-field="target" value="${esc(alias.target || '')}" placeholder="${esc(t('ai.botTarget'))}">
        <input data-ai-bot-field="aliases" value="${esc((alias.aliases || []).join(', '))}" placeholder="${esc(t('ai.botAliasNames'))}">
        <button type="button" class="secondaryButton" data-ai-bot-remove="${index}">${esc(t('ai.remove'))}</button>
      </div>
    </div>
  `).join('') : `<p class="mutedState">${esc(t('actors.none'))}</p>`;
}

function syncAiConfigListsFromDom() {
  state.aiChatProfiles = Array.from(document.querySelectorAll('[data-ai-profile-index]')).map((row, index) => ({
    id: state.aiChatProfiles[index]?.id || '',
    enabled: row.querySelector('[data-ai-profile-field="enabled"]')?.checked ?? true,
    displayName: row.querySelector('[data-ai-profile-field="displayName"]')?.value.trim() || '',
    aliases: splitAliases(row.querySelector('[data-ai-profile-field="aliases"]')?.value),
    responseFormat: row.querySelector('[data-ai-profile-field="responseFormat"]')?.value.trim() || '&b%name% &8> &f%response%',
    systemPrompt: row.querySelector('[data-ai-profile-field="systemPrompt"]')?.value.trim() || ''
  })).filter((profile) => profile.displayName);
  state.aiBotAliases = Array.from(document.querySelectorAll('[data-ai-bot-index]')).map((row, index) => ({
    id: state.aiBotAliases[index]?.id || '',
    enabled: row.querySelector('[data-ai-bot-field="enabled"]')?.checked ?? true,
    target: row.querySelector('[data-ai-bot-field="target"]')?.value.trim() || '',
    aliases: splitAliases(row.querySelector('[data-ai-bot-field="aliases"]')?.value)
  })).filter((alias) => alias.target);
}

function renderCommandMessages(messages) {
  if (!state.session?.admin || !messages) return;
  if (document.activeElement && $('commandMessagesForm').contains(document.activeElement)) return;
  $('commandMessageAbout').value = (messages.about || []).join('\n');
  $('commandMessagePlugins').value = (messages.plugins || []).join('\n');
  $('commandMessageOpDenied').value = (messages.opDenied || []).join('\n');
}

function renderAiSettings(settings) {
  if (!state.session?.admin || !settings) return;
  if (document.activeElement && document.activeElement.closest('[data-settings-scope="ai"]')) return;
  $('aiEnabled').checked = Boolean(settings.enabled);
  $('aiBaseUrl').value = settings.baseUrl || '';
  $('aiModel').value = settings.model || '';
  $('aiApiKey').value = '';
  $('aiApiKeyEnv').value = settings.apiKeyEnv || '';
  $('aiClearApiKey').checked = false;
  $('aiTemperature').value = settings.temperature ?? '';
  $('aiMaxTokens').value = settings.maxTokens ?? '';
  $('aiTimeoutSeconds').value = settings.timeoutSeconds ?? '';
  $('aiChatEnabled').checked = Boolean(settings.chatEnabled);
  $('aiChatTriggerPrefix').value = settings.chatTriggerPrefix || '';
  $('aiChatCooldownSeconds').value = settings.chatCooldownSeconds ?? '';
  $('aiChatBroadcast').checked = Boolean(settings.chatBroadcast);
  $('aiNpcEnabled').checked = Boolean(settings.npcEnabled);
  $('aiNpcAllowActions').checked = Boolean(settings.npcAllowActions);
  $('aiNpcCooldownSeconds').value = settings.npcCooldownSeconds ?? '';
  $('aiNpcResponseRadiusBlocks').value = settings.npcResponseRadiusBlocks ?? '';
  $('aiNpcCommandWhitelist').value = (settings.npcCommandWhitelist || []).join(', ');
  $('aiFakePlayersEnabled').checked = Boolean(settings.fakePlayersEnabled);
  $('aiFakePlayersIntervalSeconds').value = settings.fakePlayersIntervalSeconds ?? '';
  $('aiFakePlayersMaxActions').value = settings.fakePlayersMaxActions ?? '';
  $('aiFakePlayersMaxMoveTicks').value = settings.fakePlayersMaxMoveTicks ?? '';
  $('aiFakePlayersMaxActionTicks').value = settings.fakePlayersMaxActionTicks ?? '';
  $('aiFakePlayersNearbyRadiusBlocks').value = settings.fakePlayersNearbyRadiusBlocks ?? '';
  $('aiFakePlayersMaxPlaceDistanceBlocks').value = settings.fakePlayersMaxPlaceDistanceBlocks ?? '';
  $('aiFakePlayersAllowMovement').checked = Boolean(settings.fakePlayersAllowMovement);
  $('aiFakePlayersAllowBreaking').checked = Boolean(settings.fakePlayersAllowBreaking);
  $('aiFakePlayersAllowPlacing').checked = Boolean(settings.fakePlayersAllowPlacing);
  $('aiFakePlayersAllowInteraction').checked = Boolean(settings.fakePlayersAllowInteraction);
  $('aiFakePlayersChatControlEnabled').checked = Boolean(settings.fakePlayersChatControlEnabled);
  $('aiFakePlayersChatControlPrefix').value = settings.fakePlayersChatControlPrefix || '';
  $('aiFakePlayersChatControlCooldownSeconds').value = settings.fakePlayersChatControlCooldownSeconds ?? '';
  $('aiFakePlayersChatControlRequirePermission').checked = Boolean(settings.fakePlayersChatControlRequirePermission);
  $('aiFakePlayersChatControlPermission').value = settings.fakePlayersChatControlPermission || '';
  $('aiChatSystemPrompt').value = settings.chatSystemPrompt || '';
  $('aiNpcSystemPrompt').value = settings.npcSystemPrompt || '';
  $('aiFakePlayersSystemPrompt').value = settings.fakePlayersSystemPrompt || '';
  state.aiChatProfiles = Array.isArray(settings.chatProfiles) ? settings.chatProfiles : [];
  state.aiBotAliases = Array.isArray(settings.fakeBotAliases) ? settings.fakeBotAliases : [];
  renderAiProfileList();
  renderAiBotAliasList();
  $('aiKeyStatus').textContent = settings.apiKeyConfigured ? t('ai.keyConfigured') : t('ai.keyMissing');
}

async function refresh() {
  if (standaloneFrontend() && !state.backendUrl) {
    renderBackendConnection();
    $('serverLine').textContent = t('remote.required');
    return;
  }
  const data = await json('/api/status');
  state.lastData = data;
  state.session = data.session;
  state.csrf = data.session?.csrf || state.csrf;
  state.sessionToken = data.session?.token || state.sessionToken;
  if (state.backendUrl && state.sessionToken) storeValue(SESSION_TOKEN_KEY, state.sessionToken);
  $('serverNameTitle').textContent = data.server.name || 'HunterCore';
  $('serverLine').textContent = `${data.server.software || 'Minecraft'} · ${data.server.version}`;
  $('tps').textContent = Number(data.server.tps1).toFixed(2);
  $('mspt').textContent = Number(data.server.mspt).toFixed(1);
  $('players').textContent = `${data.server.online}/${data.server.maxPlayers}`;
  $('memory').textContent = data.server.memory;
  renderAuthPublic(data.auth);
  updateSessionChrome();
  renderHealth(data.health);
  renderBackendConnection();
  renderOverview(data);
  renderActorWorlds(data.worlds);
  renderActors(data.actorDetails);
  renderOperations(data.modules);
  renderWebUsers(data.webUsers);
  renderWebSettings(data.webSettings);
  renderCommandMessages(data.commandMessages);
  renderAiApprovals(data.aiApprovals);
  renderAiSettings(data.aiSettings);
  refreshChat().catch(() => {});
  const targetInterval = Math.max(1500, Math.min(15000, Number(data.optimization?.guestStatusCacheMillis || 5000) * 2));
  if (state.refreshTimer && state.pollMillis !== targetInterval) {
    clearInterval(state.refreshTimer);
    state.refreshTimer = setInterval(() => {
      refresh().catch(() => {});
    }, targetInterval);
  }
  state.pollMillis = targetInterval;
}

async function refreshMap() {
  if (standaloneFrontend() && !state.backendUrl) return;
  const map = await json('/api/map');
  if (!map.ok || !map.url || map.url === state.mapUrl) return;
  state.mapUrl = map.url;
  $('mapLink').href = map.url;
  $('mapFrame').src = map.url;
}

async function runCommand(command) {
  const payload = JSON.stringify({ command });
  const result = await json('/api/command', { method: 'POST', body: payload });
  setOutput(result.message || t('command.dispatched'), result.output || '');
  await refresh();
}

function editWebUser(id) {
  const user = state.webUsers.find((candidate) => candidate.id === id);
  if (!user) return;
  $('webUserName').value = user.displayName;
  $('webUserRole').value = user.role;
  $('webUserPassword').value = '';
  $('webUserCommandExecution').checked = Boolean(user.commandExecution);
  $('webUserAllowedMode').value = user.allowedCommandsConfigured
    ? (user.allowedCommands?.length ? 'custom' : 'none')
    : 'inherit';
  $('webUserAllowedCommands').value = user.allowedCommands?.join(', ') || '';
}

function updateActorKind() {
  const fake = $('actorModule').value === 'fake-players' || $('actorModule').value === 'real-fake-players';
  $('actorKind').disabled = fake;
  if (fake) $('actorKind').value = 'mannequin';
}

function setConfigPanel(workbench, targetName) {
  const buttons = Array.from(workbench.querySelectorAll('[data-config-target]'));
  const panels = Array.from(workbench.querySelectorAll('[data-config-panel]'));
  const fallback = buttons[0]?.dataset.configTarget || panels[0]?.dataset.configPanel || '';
  const selected = panels.some((panel) => panel.dataset.configPanel === targetName) ? targetName : fallback;
  buttons.forEach((button) => {
    const active = button.dataset.configTarget === selected;
    button.classList.toggle('isActive', active);
    button.setAttribute('aria-selected', String(active));
  });
  panels.forEach((panel) => {
    panel.hidden = panel.dataset.configPanel !== selected;
  });
}

function bindConfigWorkbenches() {
  $$('[data-config-workbench]').forEach((workbench) => {
    const storageKey = `${CONFIG_WORKBENCH_KEY_PREFIX}${workbench.dataset.configWorkbench || 'default'}`;
    workbench.querySelectorAll('[data-config-target]').forEach((button) => {
      button.addEventListener('click', () => {
        storeValue(storageKey, button.dataset.configTarget || '');
        setConfigPanel(workbench, button.dataset.configTarget);
      });
    });
    const active = workbench.querySelector('.configTab.isActive');
    setConfigPanel(workbench, storedValue(storageKey) || active?.dataset.configTarget);
  });
}

function liquidGlassElement(target) {
  return target instanceof Element ? target.closest(liquidGlassSelector) : null;
}

function updateLiquidGlassPointer(element, event) {
  const rect = element.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) return;
  const x = Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100));
  const y = Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height) * 100));
  const tiltX = ((x - 50) / 50) * 3.6;
  const tiltY = ((50 - y) / 50) * 3.2;
  element.style.setProperty('--glass-x', `${x.toFixed(2)}%`);
  element.style.setProperty('--glass-y', `${y.toFixed(2)}%`);
  element.style.setProperty('--tilt-x', `${tiltX.toFixed(2)}deg`);
  element.style.setProperty('--tilt-y', `${tiltY.toFixed(2)}deg`);
}

function relaxLiquidGlass(element) {
  element.style.setProperty('--tilt-x', '0deg');
  element.style.setProperty('--tilt-y', '0deg');
  element.style.setProperty('--press', '0');
}

function bindLiquidGlass() {
  let pressed = null;
  let pending = null;
  let frame = 0;

  document.addEventListener('pointermove', (event) => {
    const element = liquidGlassElement(event.target);
    if (!element) return;
    pending = { element, event };
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      if (!pending) return;
      updateLiquidGlassPointer(pending.element, pending.event);
      pending = null;
    });
  }, { passive: true });

  document.addEventListener('pointerdown', (event) => {
    const element = liquidGlassElement(event.target);
    if (!element) return;
    updateLiquidGlassPointer(element, event);
    element.style.setProperty('--press', '1');
    pressed = element;
  }, { passive: true });

  document.addEventListener('pointerup', () => {
    if (!pressed) return;
    pressed.style.setProperty('--press', '0');
    pressed = null;
  }, { passive: true });

  document.addEventListener('pointercancel', () => {
    if (!pressed) return;
    pressed.style.setProperty('--press', '0');
    pressed = null;
  }, { passive: true });

  document.addEventListener('pointerout', (event) => {
    const element = liquidGlassElement(event.target);
    if (!element) return;
    if (event.relatedTarget instanceof Node && element.contains(event.relatedTarget)) return;
    relaxLiquidGlass(element);
  }, { passive: true });
}

function bindServerIcon() {
  const mark = $('productMark');
  const image = $('serverIcon');
  if (!mark || !(image instanceof HTMLImageElement)) return;
  image.addEventListener('load', () => {
    mark.classList.add('hasIcon');
    image.hidden = false;
  });
  image.addEventListener('error', () => {
    mark.classList.remove('hasIcon');
    image.hidden = true;
  });
  image.src = `${assetUrl('/assets/server-icon.png')}?${Date.now()}`;
}

function bindEvents() {
  $$('.navButton[data-page-target]').forEach((button) => {
    button.addEventListener('click', () => {
      showPage(button.dataset.pageTarget);
    });
  });

  window.addEventListener('popstate', () => showPage(pageFromLocation(), false));

  $('languageToggle').addEventListener('click', () => {
    setLanguage(state.lang === 'zh' ? 'en' : 'zh');
  });

  $('sessionToggle').addEventListener('click', () => {
    openAuthModal('login');
  });

  $('registerToggle').addEventListener('click', () => openAuthModal('register'));
  $('authBackdrop').addEventListener('click', closeAuthModals);
  $$('[data-close-auth]').forEach((button) => button.addEventListener('click', closeAuthModals));
  window.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeAuthModals();
  });

  $('loginForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const payload = JSON.stringify({ username: $('username').value, password: $('password').value });
      const result = await json('/api/login', { method: 'POST', body: payload });
      $('password').value = '';
      state.session = result.session;
      state.csrf = result.session?.csrf || '';
      state.sessionToken = result.session?.token || '';
      storeValue(SESSION_TOKEN_KEY, state.backendUrl ? state.sessionToken : '');
      setOutput(t('command.loggedIn', { username: result.session.username, role: roleLabel(result.session.role) }));
      closeAuthModals();
      await refresh();
    } catch {
      $('password').value = '';
      setOutput(t('command.loginFailed'));
    }
  });

  $('logoutButton').addEventListener('click', async () => {
    await json('/api/logout', { method: 'POST' });
    state.session = null;
    state.csrf = '';
    state.sessionToken = '';
    storeValue(SESSION_TOKEN_KEY, '');
    setOutput(t('command.loggedOut'));
    closeAuthModals();
    await refresh();
  });

  $('registerForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const username = $('registerUsername').value;
      const password = $('registerPassword').value;
      await json('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          username,
          password,
          confirmPassword: $('registerConfirmPassword').value
        })
      });
      const login = await json('/api/login', { method: 'POST', body: JSON.stringify({ username, password }) });
      $('registerPassword').value = '';
      $('registerConfirmPassword').value = '';
      state.session = login.session;
      state.csrf = login.session?.csrf || '';
      state.sessionToken = login.session?.token || '';
      storeValue(SESSION_TOKEN_KEY, state.backendUrl ? state.sessionToken : '');
      closeAuthModals();
      setOutput(t('command.loggedIn', { username: login.session.username, role: roleLabel(login.session.role) }));
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('homeChatForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const message = $('homeChatInput').value.trim();
    if (!message) return;
    try {
      const result = await json('/api/chat/send', { method: 'POST', body: JSON.stringify({ message }) });
      $('homeChatInput').value = '';
      if (result.chat?.lines) renderHomeChat(result.chat.lines);
      else await refreshChat();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('connectionForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    state.backendUrl = normalizeBackendUrl($('backendUrl').value);
    state.apiKey = $('backendApiKey').value.trim();
    state.csrf = '';
    state.sessionToken = state.backendUrl ? state.sessionToken : '';
    storeValue(BACKEND_URL_KEY, state.backendUrl);
    storeValue(BACKEND_API_KEY_KEY, state.apiKey);
    storeValue(SESSION_TOKEN_KEY, state.sessionToken);
    renderBackendConnection();
    bindServerIcon();
    setOutput(t('remote.saved'));
    await refresh();
    await refreshMap();
  });

  $('clearConnectionButton').addEventListener('click', async () => {
    state.backendUrl = '';
    state.apiKey = '';
    state.sessionToken = '';
    state.csrf = '';
    storeValue(BACKEND_URL_KEY, '');
    storeValue(BACKEND_API_KEY_KEY, '');
    storeValue(SESSION_TOKEN_KEY, '');
    $('backendApiKey').value = '';
    renderBackendConnection();
    bindServerIcon();
    setOutput(t('remote.local'));
    await refresh();
    await refreshMap();
  });

  $('commandForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      await runCommand($('commandInput').value);
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $$('.quickRow [data-command]').forEach((button) => {
    button.addEventListener('click', async () => {
      $('commandInput').value = button.dataset.command;
      try {
        await runCommand(button.dataset.command);
      } catch (error) {
        setOutput(t('command.error', { message: error.message }));
      }
    });
  });

  $('actorModule').addEventListener('change', updateActorKind);

  $('actorForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      module: $('actorModule').value,
      kind: $('actorKind').value,
      name: $('actorName').value
    };
    if ($('actorWorld').value || $('actorX').value || $('actorY').value || $('actorZ').value) {
      payload.world = $('actorWorld').value;
      payload.x = $('actorX').value;
      payload.y = $('actorY').value;
      payload.z = $('actorZ').value;
    }
    try {
      const result = await json('/api/admin/actor/spawn', { method: 'POST', body: JSON.stringify(payload) });
      setOutput(result.message || t('actors.spawned'), result.output || '');
      if (result.ok) $('actorName').value = '';
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('actorList').addEventListener('click', async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement)) return;
    try {
      const payload = { module: target.dataset.actorModule, id: target.dataset.actorId };
      if (target.dataset.actorClickSave || target.dataset.actorClickClear) {
        const row = target.closest('.dataItem');
        const input = row?.querySelector('[data-actor-command-input]');
        payload.command = target.dataset.actorClickClear ? '' : (input?.value || '');
        const result = await json('/api/admin/actor/click-command', { method: 'POST', body: JSON.stringify(payload) });
        setOutput(result.message || t('actors.clickSaved'), '');
      } else if (target.dataset.actorAiSave) {
        const row = target.closest('.dataItem');
        const enabled = row?.querySelector('[data-actor-ai-enabled]');
        const persona = row?.querySelector('[data-actor-ai-persona]');
        payload.enabled = String(Boolean(enabled?.checked));
        payload.persona = persona?.value || '';
        const result = await json('/api/admin/actor/ai', { method: 'POST', body: JSON.stringify(payload) });
        setOutput(result.message || t('actors.aiSaved'), '');
      } else if (target.dataset.actorRemove) {
        const result = await json('/api/admin/actor/remove', { method: 'POST', body: JSON.stringify(payload) });
        setOutput(result.message || t('actors.removed'), result.output || '');
      } else {
        return;
      }
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('opsPanel').addEventListener('change', async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    const endpoint = target.dataset.module ? '/api/admin/module' : '/api/admin/command';
    const payload = target.dataset.module
      ? { module: target.dataset.module, enabled: String(target.checked) }
      : { module: target.dataset.commandModule, command: target.dataset.command, enabled: String(target.checked) };
    try {
      const result = await json(endpoint, { method: 'POST', body: JSON.stringify(payload) });
      setOutput(result.message || t('action.updated'), result.output || '');
      await refresh();
    } catch (error) {
      target.checked = !target.checked;
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('luckForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      action: $('luckAction').value,
      target: $('luckTarget').value,
      group: $('luckGroup').value,
      permission: $('luckPermission').value,
      value: $('luckValue').value
    };
    try {
      const result = await json('/api/admin/luckperms', { method: 'POST', body: JSON.stringify(payload) });
      setOutput(result.message || t('luck.dispatched'), result.output || '', result.editorUrl || '');
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('webSettingsForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      serverName: $('webServerName').value,
      cpuMode: $('webCpuMode').value,
      f3ServerName: $('webF3ServerName').value,
      bindAddress: $('webBindAddress').value,
      port: $('webPort').value,
      externalUrl: $('webExternalUrl').value,
      mapUrl: $('webMapUrl').value,
      publicMap: String($('webPublicMap').checked),
      tpsDisplayEnabled: String($('tpsDisplayEnabled').checked),
      tpsActionbar: String($('tpsActionbar').checked),
      tpsIntervalTicks: $('tpsIntervalTicks').value,
      tpsActionbarFormat: $('tpsActionbarFormat').value,
      sidebarEnabled: String($('sidebarEnabled').checked),
      sidebarTitle: $('sidebarTitle').value,
      sidebarIntervalTicks: $('sidebarIntervalTicks').value,
      sidebarDirtyUpdatesOnly: String($('sidebarDirtyUpdatesOnly').checked),
      sidebarLines: $('sidebarLines').value,
      motdEnabled: String($('motdEnabled').checked),
      motdLine1: $('motdLine1').value,
      motdLine2: $('motdLine2').value,
      motdMaxPlayers: $('motdMaxPlayers').value,
      authEnabled: String($('authEnabled').checked),
      authRegistrationRequired: String($('authRegistrationRequired').checked),
      authWebRegistrationRequired: String($('authWebRegistrationRequired').checked),
      authWebRegistrationEnabled: String($('authWebRegistrationEnabled').checked),
      authWebLoginEnabled: String($('authWebLoginEnabled').checked),
      authGuiEnabled: String($('authGuiEnabled').checked),
      authOpenGuiOnJoin: String($('authOpenGuiOnJoin').checked),
      authMinimumPasswordLength: $('authMinimumPasswordLength').value,
      authRegistrationUrl: $('authRegistrationUrl').value,
      corsEnabled: String($('webCorsEnabled').checked),
      corsAllowOrigin: $('webCorsAllowOrigin').value,
      apiKeyEnabled: String($('webApiKeyEnabled').checked),
      apiKey: $('webApiKey').value,
      clearApiKey: String($('webClearApiKey').checked)
    };
    try {
      const result = await json('/api/admin/web-settings', { method: 'POST', body: JSON.stringify(payload) });
      setOutput(result.restart ? t('webSettings.restarting') : (result.threadingChanged ? t('webSettings.threadingSaved') : t('webSettings.saved')));
      $('webApiKey').value = '';
      if (result.settings) renderWebSettings(result.settings);
      await refresh();
      await refreshMap();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('commandMessagesForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      about: $('commandMessageAbout').value,
      plugins: $('commandMessagePlugins').value,
      opDenied: $('commandMessageOpDenied').value
    };
    try {
      const result = await json('/api/admin/command-messages', { method: 'POST', body: JSON.stringify(payload) });
      setOutput(t('commandMessages.saved'));
      if (result.messages) renderCommandMessages(result.messages);
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('aiAddChatProfile').addEventListener('click', () => {
    syncAiConfigListsFromDom();
    state.aiChatProfiles.push({
      id: '',
      enabled: true,
      displayName: 'AI',
      aliases: ['AI'],
      responseFormat: '&b%name% &8> &f%response%',
      systemPrompt: $('aiChatSystemPrompt').value || ''
    });
    renderAiProfileList();
  });

  $('aiAddBotAlias').addEventListener('click', () => {
    syncAiConfigListsFromDom();
    state.aiBotAliases.push({ id: '', enabled: true, target: '', aliases: [] });
    renderAiBotAliasList();
  });

  $('aiChatProfileList').addEventListener('click', (event) => {
    const button = event.target.closest('[data-ai-profile-remove]');
    if (!button) return;
    syncAiConfigListsFromDom();
    state.aiChatProfiles.splice(Number(button.dataset.aiProfileRemove), 1);
    renderAiProfileList();
  });

  $('aiBotAliasList').addEventListener('click', (event) => {
    const button = event.target.closest('[data-ai-bot-remove]');
    if (!button) return;
    syncAiConfigListsFromDom();
    state.aiBotAliases.splice(Number(button.dataset.aiBotRemove), 1);
    renderAiBotAliasList();
  });

  $('aiSettingsForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    syncAiConfigListsFromDom();
    const payload = {
      enabled: String($('aiEnabled').checked),
      provider: 'openai-compatible',
      baseUrl: $('aiBaseUrl').value,
      model: $('aiModel').value,
      apiKey: $('aiApiKey').value,
      clearApiKey: String($('aiClearApiKey').checked),
      apiKeyEnv: $('aiApiKeyEnv').value,
      temperature: $('aiTemperature').value,
      maxTokens: $('aiMaxTokens').value,
      timeoutSeconds: $('aiTimeoutSeconds').value,
      chatEnabled: String($('aiChatEnabled').checked),
      chatTriggerPrefix: $('aiChatTriggerPrefix').value,
      chatCooldownSeconds: $('aiChatCooldownSeconds').value,
      chatBroadcast: String($('aiChatBroadcast').checked),
      chatSystemPrompt: $('aiChatSystemPrompt').value,
      chatProfiles: JSON.stringify(state.aiChatProfiles),
      npcEnabled: String($('aiNpcEnabled').checked),
      npcCooldownSeconds: $('aiNpcCooldownSeconds').value,
      npcResponseRadiusBlocks: $('aiNpcResponseRadiusBlocks').value,
      npcAllowActions: String($('aiNpcAllowActions').checked),
      npcSystemPrompt: $('aiNpcSystemPrompt').value,
      npcCommandWhitelist: $('aiNpcCommandWhitelist').value,
      fakePlayersEnabled: String($('aiFakePlayersEnabled').checked),
      fakePlayersIntervalSeconds: $('aiFakePlayersIntervalSeconds').value,
      fakePlayersMaxActions: $('aiFakePlayersMaxActions').value,
      fakePlayersMaxMoveTicks: $('aiFakePlayersMaxMoveTicks').value,
      fakePlayersMaxActionTicks: $('aiFakePlayersMaxActionTicks').value,
      fakePlayersNearbyRadiusBlocks: $('aiFakePlayersNearbyRadiusBlocks').value,
      fakePlayersMaxPlaceDistanceBlocks: $('aiFakePlayersMaxPlaceDistanceBlocks').value,
      fakePlayersAllowMovement: String($('aiFakePlayersAllowMovement').checked),
      fakePlayersAllowBreaking: String($('aiFakePlayersAllowBreaking').checked),
      fakePlayersAllowPlacing: String($('aiFakePlayersAllowPlacing').checked),
      fakePlayersAllowInteraction: String($('aiFakePlayersAllowInteraction').checked),
      fakePlayersChatControlEnabled: String($('aiFakePlayersChatControlEnabled').checked),
      fakePlayersChatControlPrefix: $('aiFakePlayersChatControlPrefix').value,
      fakePlayersChatControlCooldownSeconds: $('aiFakePlayersChatControlCooldownSeconds').value,
      fakePlayersChatControlRequirePermission: String($('aiFakePlayersChatControlRequirePermission').checked),
      fakePlayersChatControlPermission: $('aiFakePlayersChatControlPermission').value,
      fakeBotAliases: JSON.stringify(state.aiBotAliases),
      fakePlayersSystemPrompt: $('aiFakePlayersSystemPrompt').value
    };
    try {
      const result = await json('/api/admin/ai-settings', { method: 'POST', body: JSON.stringify(payload) });
      $('aiApiKey').value = '';
      $('aiClearApiKey').checked = false;
      setOutput(t('ai.saved'));
      if (result.settings) renderAiSettings(result.settings);
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('aiTestForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const result = await json('/api/admin/ai-test', { method: 'POST', body: JSON.stringify({ prompt: $('aiTestPrompt').value }) });
      setOutput(t('ai.testDone'), result.response || '');
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('webUserForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      username: $('webUserName').value,
      role: $('webUserRole').value,
      password: $('webUserPassword').value,
      commandExecution: String($('webUserCommandExecution').checked),
      allowedCommandsMode: $('webUserAllowedMode').value,
      allowedCommands: $('webUserAllowedCommands').value
    };
    try {
      await json('/api/admin/web-user/save', { method: 'POST', body: JSON.stringify(payload) });
      $('webUserPassword').value = '';
      setOutput(t('webUsers.saved'));
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('webUserList').addEventListener('click', async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement)) return;
    if (target.dataset.userEdit) {
      editWebUser(target.dataset.userEdit);
      return;
    }
    if (!target.dataset.userRemove) return;
    try {
      await json('/api/admin/web-user/remove', { method: 'POST', body: JSON.stringify({ username: target.dataset.userRemove }) });
      setOutput(t('webUsers.removed'));
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('pluginList').addEventListener('click', async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement) || !target.dataset.pluginAction) return;
    try {
      const payload = {
        plugin: target.dataset.pluginName,
        action: target.dataset.pluginAction
      };
      target.disabled = true;
      const result = await json('/api/admin/plugin', { method: 'POST', body: JSON.stringify(payload) });
      setOutput(result.message || t('plugin.actionCompleted'), result.output || '');
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
      await refresh();
    }
  });

  $('pluginList').addEventListener('submit', async (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || !form.dataset.pluginUpdate) return;
    event.preventDefault();
    const input = form.querySelector('input[name="updateUrl"]');
    const button = form.querySelector('button[type="submit"]');
    const url = input instanceof HTMLInputElement ? input.value.trim() : '';
    if (!url) {
      setOutput(t('plugin.updateUrlRequired'));
      return;
    }
    try {
      if (button instanceof HTMLButtonElement) button.disabled = true;
      const payload = { plugin: form.dataset.pluginUpdate, url };
      const result = await json('/api/admin/plugin-update', { method: 'POST', body: JSON.stringify(payload) });
      setOutput(result.message || t('plugin.updateCompleted'), result.output || '');
      if (input instanceof HTMLInputElement) input.value = '';
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
      await refresh();
    }
  });

  $('aiApprovalList')?.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-ai-approval]');
    if (!button) return;
    try {
      const result = await json('/api/admin/ai-approval', {
        method: 'POST',
        body: JSON.stringify({
          name: button.dataset.aiApproval,
          action: button.dataset.aiAction
        })
      });
      setOutput(result.message || t('command.dispatched'), result.output || '');
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });
}

applyTranslations();
renderBackendConnection();
bindConfigWorkbenches();
bindLiquidGlass();
bindServerIcon();
bindEvents();
showPage(pageFromLocation(), false);
updateActorKind();
renderBackendConnection();
refresh()
  .then(() => {
    if (window.location.hash === '#register') openAuthModal('register');
    if (window.location.hash === '#login') openAuthModal('login');
  })
  .catch((error) => {
    $('serverLine').textContent = error.message;
  });
refreshMap().catch(() => {});
state.refreshTimer = setInterval(() => {
  refresh().catch(() => {});
}, standaloneFrontend() && !state.backendUrl ? 15000 : 5000);
state.pollMillis = 5000;
