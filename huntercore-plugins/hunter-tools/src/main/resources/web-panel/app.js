const LANG_KEY = 'huntercore.panel.language';
const BACKEND_URL_KEY = 'huntercore.panel.backendUrl';
const BACKEND_API_KEY_KEY = 'huntercore.panel.apiKey';
const SESSION_TOKEN_KEY = 'huntercore.panel.sessionToken';
const CONFIG_WORKBENCH_KEY_PREFIX = 'huntercore.panel.config.';
const PLUGIN_PAGE_SIZE = 20;

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

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || '');
      resolve(result.includes(',') ? result.split(',').pop() : result);
    };
    reader.onerror = () => reject(new Error('Failed to read file.'));
    reader.readAsDataURL(file);
  });
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
  selectedPlugin: '',
  pluginPage: 0,
  selectedActor: '',
  selectedWebUser: '',
  page: 'map',
  mode: panelMode(),
  connection: {
    status: 'idle',
    detail: '',
    lastSuccessAt: 0
  },
  modal: {
    kind: '',
    opener: null
  },
  activeForm: null,
  aiChatProfiles: [],
  aiBotAliases: [],
  aiFakePersonas: [],
  storyPhases: [],
  chatLines: [],
  huntEngineOperations: []
};

const $ = (id) => document.getElementById(id);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));
const ADMIN_PAGES = ['titles', 'settings', 'access', 'ai', 'admin'];
const HUNT_ENGINE_PAGES = ['hunt-engine'];

const FIELD_HELP = {
  webServerName: 'Shown as the panel title and %server% display name.',
  webF3ServerName: 'Shown in the Minecraft F3 brand line. Supports & color codes.',
  webCpuMode: 'Threading preset. Use single-thread for stability, high-core for stronger CPUs.',
  webBindAddress: 'Use 0.0.0.0 for LAN access, 127.0.0.1 for local-only.',
  webPort: 'The web panel HTTP port.',
  webExternalUrl: 'Public URL players should open for the panel or registration.',
  webMapUrl: 'BlueMap URL. %host% is replaced by the panel host.',
  tpsIntervalTicks: '20 ticks is about 1 second.',
  tpsActionbarFormat: 'Supports & color codes and placeholders like %tps% and %mspt%.',
  sidebarTitle: 'Sidebar title shown in game. Supports & color codes.',
  sidebarIntervalTicks: 'How often the sidebar refreshes, in Minecraft ticks.',
  sidebarLines: 'One sidebar line per row. Use placeholders listed below.',
  motdLine1: 'First server list MOTD line. Supports & color codes.',
  motdLine2: 'Second server list MOTD line. Supports placeholders.',
  motdMaxPlayers: '-1 keeps the server default max player count.',
  commandMessageAbout: 'Lines shown when a player runs /about.',
  commandMessagePlugins: 'Lines shown when a player runs /plugins or /pl.',
  commandMessageVersion: 'Lines shown when a player runs /version or /ver.',
  commandMessageRules: 'Lines shown when a player runs /rules.',
  commandMessageDiscord: 'Lines shown when a player runs /discord.',
  commandMessageWebsite: 'Lines shown when a player runs /website or /site.',
  commandMessageMotd: 'Lines shown when a player runs /motd.',
  commandMessageInfo: 'Lines shown when a player runs /info.',
  commandMessageServer: 'Lines shown when a player runs /server.',
  commandMessageLinks: 'Lines shown when a player runs /links.',
  commandMessageQq: 'Lines shown when a player runs /qq.',
  commandMessageGroup: 'Lines shown when a player runs /group.',
  commandMessageOpDenied: 'Lines shown when a non-admin player tries /op.',
  authMinimumPasswordLength: 'Minimum password length for /register and web registration.',
  authLoginTimeoutSeconds: 'Seconds before unauthenticated players are kicked; set 0 to disable the timeout.',
  authMaxLoginAttempts: 'Wrong password attempts before a temporary login lock is applied.',
  authLockoutSeconds: 'Seconds a player must wait after too many wrong passwords.',
  authRegistrationUrl: 'URL shown to unregistered players when web pre-registration is required.',
  webCorsAllowOrigin: 'Use * for testing, or a specific frontend origin in production.',
  webApiKey: 'Optional API key for standalone frontend/backend separation.',
  aiBaseUrl: 'OpenAI-compatible API base URL, for example https://api.openai.com/v1.',
  aiModel: 'Model name sent to the AI provider.',
  aiApiKey: 'Stored server-side. Leave blank to keep the existing key.',
  aiApiKeyEnv: 'Environment variable name used when no key is stored.',
  aiTemperature: 'Higher is more creative; lower is more deterministic.',
  aiMaxTokens: 'Maximum response size. Fake player actions usually need 200-600.',
  aiTimeoutSeconds: 'AI request timeout in seconds.',
  aiChatTriggerPrefix: 'Names or prefixes that trigger chat AI.',
  aiFakePlayersIntervalSeconds: 'Seconds between autonomous fake-player AI turns.',
  aiFakePlayersMaxActions: 'Maximum bracketed actions applied from one AI response.',
  aiFakePlayersMaxMoveTicks: 'Maximum movement duration. 200 ticks is about 10 seconds.',
  aiFakePlayersMaxActionTicks: 'Maximum duration for mining, attacking, using, and other repeated actions.',
  aiFakePlayersNearbyRadiusBlocks: 'How far the AI can observe nearby blocks, players, and entities.',
  aiFakePlayersMaxPlaceDistanceBlocks: 'How far a fake player may place blocks from itself.',
  aiFakePlayersAllowMovement: 'Allow AI actions such as move, goto, follow, jump, sneak, and sprint.',
  aiFakePlayersAllowBreaking: 'Allow AI mining and block breaking actions.',
  aiFakePlayersAllowPlacing: 'Allow AI block placement, building presets, and WorldEdit fill/clear actions.',
  aiFakePlayersAllowInteraction: 'Allow AI right-click/use interactions.',
  aiFakePlayersQuickResponseMode: 'off means the model decides all actions. locked keeps local quick actions but blocks duplicate model actions.',
  aiFakePlayersChatControlEnabled: 'When enabled, player chat mentioning a bot name or alias can assign that bot a task.',
  aiFakePlayersChatControlPrefix: 'Optional command prefix. Bot names also trigger directly.',
  aiFakePlayersChatControlCooldownSeconds: 'Minimum seconds before the same player can assign another chat task.',
  aiFakePlayersChatControlRequirePermission: 'Require the configured permission before chat can control AI bots.',
  aiFakePlayersChatControlPermission: 'Permission node checked when chat control permission is required.',
  aiFakePlayersSystemPrompt: 'Advanced instruction for real fake player AI. Use action syntax only.'
};
const GUI_PERMISSION_FIELDS = [
  ['admin', 'Admin GUI / hc admin'],
  ['fake-players', 'Playerbots GUI'],
  ['npcs', 'NPC GUI'],
  ['story', 'Story GUI'],
  ['titles', 'Titles GUI'],
  ['title-admin', 'Title administration'],
  ['teleport', 'TPA / teleport GUI'],
  ['homes', 'Homes GUI'],
  ['random-teleport', 'Random teleport'],
  ['hunt-engine', 'HuntEngine catalogue'],
  ['hunt-engine-give', 'HuntEngine content give'],
  ['hunt-engine-admin', 'HuntEngine administration'],
  ['auth', 'Auth GUI'],
  ['tps', 'TPS'],
  ['heal', 'Heal'],
  ['feed', 'Feed'],
  ['fly', 'Fly'],
  ['gamemode', 'Game mode'],
  ['time', 'Time'],
  ['weather', 'Weather'],
  ['broadcast', 'Broadcast'],
  ['clearchat', 'Clear chat'],
  ['speed', 'Speed'],
  ['spawn', 'Spawn'],
  ['setspawn', 'Set spawn'],
  ['back', 'Back'],
  ['hat', 'Hat'],
  ['craft', 'Crafting table'],
  ['enderchest', 'Ender chest'],
  ['trash', 'Trash'],
  ['menu', 'Main GUI'],
  ['profile', 'Profile GUI'],
  ['settings', 'Settings GUI']
];
const PAGES = ['map', 'overview', 'runtime', 'plugins', 'tools', ...HUNT_ENGINE_PAGES, ...ADMIN_PAGES];

const translations = {
  zh: {
    'server.loading': '正在读取服务器状态...',
    'nav.map': '地图',
    'nav.overview': '总览',
    'nav.runtime': '运行时',
    'nav.plugins': '插件',
    'nav.tools': '工具',
    'nav.huntEngine': 'HuntEngine',
    'nav.titles': '称号',
    'nav.settings': '设置',
    'nav.access': '权限',
    'nav.ai': 'AI',
    'nav.admin': '管理',
    'nav.more': '更多',
    'language.switch': '切换到 English',
    'session.eyebrow': '网页控制台',
    'session.guest': '访客视图',
    'login.username': '玩家名 / 面板管理员',
    'login.password': '游戏密码 / 面板密码',
    'login.action': '登录',
    'identity.claimRequired': '此网页账号尚未绑定游戏 UUID。请进入服务器使用 /login <网页密码> 认领；online-mode 服务器也可使用 /register <新密码> <新密码> 认领。认领完成前，聊天与命令已禁用。',
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
    'connection.action': '连接',
    'connection.eyebrow': '连接状态',
    'connection.title': '后端连接',
    'connection.backendUrl': '后端地址',
    'connection.apiKey': 'API 密钥',
    'connection.local': '同源面板',
    'connection.connecting': '正在连接',
    'connection.online': '已连接',
    'connection.offline': '后端不可用',
    'connection.stale': '数据已过期',
    'connection.required': '需要后端地址',
    'connection.updated': '最近更新：{time}',
    'form.unsaved': '有未保存的更改。自动刷新不会覆盖此表单。',
    'form.saving': '正在保存…',
    'remote.corsEnabled': '允许独立前端跨域',
    'remote.corsAllowOrigin': '允许来源，例如 * 或 https://panel.example.com',
    'remote.apiKeyEnabled': '允许 API key 管理',
    'remote.clearApiKey': '清空 API key',
    'metric.online': '在线',
    'metric.memory': '内存',
    'map.open': '在新标签打开地图',
    'map.stateEyebrow': '地图状态',
    'map.loadingTitle': '正在准备地图',
    'map.loadingDescription': '正在检查地图服务…',
    'map.emptyTitle': '尚未连接地图',
    'map.emptyDescription': '连接后端后即可加载地图，或请管理员检查地图地址设置。',
    'map.errorTitle': '地图暂时不可用',
    'map.errorDescription': '无法读取地图地址。请检查后端连接或稍后重试。',
    'map.retry': '重新检查',
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
    'plugins.page': '第 {page} / {pages} 页 · 共 {count} 个',
    'plugins.prevPage': '上一页',
    'plugins.nextPage': '下一页',
    'plugins.search': '搜索插件、Jar、描述或作者',
    'plugins.empty': '没有匹配的插件。',
    'plugins.summary': '全部 {total} · 已启用 {enabled} · 已停用 {disabled} · 仅安装 {installed} · 受保护 {protected}',
    'plugins.releaseReady': '发行检查',
    'plugins.releaseReadyOk': '插件发行状态良好',
    'plugins.releaseReadyWarn': '{count} 个插件项需要关注',
    'plugins.issue.missingDependency': '缺少依赖',
    'plugins.issue.installedOnly': '仅安装未加载',
    'plugins.issue.disabled': '已停用',
    'plugins.issue.legacyApi': '旧 API',
    'plugins.issue.legacyNoChatReports': '旧外部 NoChatReports',
    'plugins.issue.noDescriptor': '描述文件缺失',
    'plugins.legacyNoChatReports': '旧外部 NoChatReports 已由核心内置聊天举报保护替代，可移除该外部插件。',
    'plugins.detail': '详情',
    'plugins.filter.all': '全部',
    'plugins.filter.enabled': '已启用',
    'plugins.filter.disabled': '已停用',
    'plugins.filter.installed': '仅安装',
    'plugins.filter.protected': '受保护',
    'plugins.filter.actionable': '可操作',
    'worlds.none': '暂无已加载世界。',
    'players.none': '当前没有玩家在线。',
    'tools.eyebrow': 'Minecraft 操作',
    'tools.title': '工具',
    'huntEngine.eyebrow': '自定义内容运营',
    'huntEngine.title': 'HuntEngine',
    'huntEngine.catalogue': '内容目录',
    'huntEngine.catalogueHint': '所有内容定义由引擎管理；面板不会直接写入引擎 YAML。',
    'huntEngine.packages': '原生内容包',
    'huntEngine.packagesHint': '上传原生 ZIP 内容包到 HuntEngine 暂存区。',
    'huntEngine.simpleItem': '安全简单物品向导',
    'huntEngine.simpleItemHint': '创建一个固定 huntercraft 命名空间中的原版物品。模型、方块、家具、配方、字体、图片和声音仍须通过已审核的原生 ZIP 内容包导入。',
    'huntEngine.simpleItemId': '物品 ID',
    'huntEngine.simpleItemIdPlaceholder': 'starter_token',
    'huntEngine.simpleItemIdHint': '固定内容 ID：huntercraft:<id>。仅可使用小写字母、数字、_ 或 -。',
    'huntEngine.simpleItemMaterial': '原版材料',
    'huntEngine.simpleItemMaterialHint': '向导只提供安全的 Bukkit 材料白名单。',
    'huntEngine.simpleItemName': '显示名称',
    'huntEngine.simpleItemNamePlaceholder': '新手代币',
    'huntEngine.simpleItemDescription': '描述',
    'huntEngine.simpleItemDescriptionPlaceholder': '一份小小的欢迎礼物。',
    'huntEngine.simpleItemTextHint': '仅允许纯文本；不接受格式化、模板或点击操作。',
    'huntEngine.createSimpleItem': '创建并暂存物品',
    'huntEngine.simpleItemStaged': '简单物品 {id} 已创建并暂存。请校验、构建并发布后再使用。',
    'huntEngine.upload': '暂存内容包',
    'huntEngine.operations': '构建与发布',
    'huntEngine.operationsHint': '构建、发布与重载均异步执行；失败时当前资源包不会被替换。',
    'huntEngine.validate': '校验',
    'huntEngine.build': '构建',
    'huntEngine.publish': '发布',
    'huntEngine.reload': '重载',
    'huntEngine.sendPackPlayer': '在线玩家',
    'huntEngine.sendPack': '发送当前资源包',
    'huntEngine.migration': '迁移诊断',
    'huntEngine.settingsManaged': '资源包生命周期由 HuntEngine 工作台管理。',
    'huntEngine.contents': '内容',
    'huntEngine.staged': '暂存包',
    'huntEngine.resourcePack': '资源包',
    'huntEngine.migrationSummary': '迁移',
    'huntEngine.available': '引擎可用',
    'huntEngine.unavailable': '引擎不可用',
    'huntEngine.packPublished': '当前资源包已发布',
    'huntEngine.packConfigured': '资源包已配置，等待发布',
    'huntEngine.packMissing': '尚无可用资源包',
    'huntEngine.noContents': '目录中暂无内容。',
    'huntEngine.noPackages': '暂存区为空。',
    'huntEngine.removePackage': '移除',
    'huntEngine.noOperations': '尚未开始操作。',
    'huntEngine.noMigration': '暂无迁移记录。',
    'huntEngine.operationStarted': 'HuntEngine 操作已开始。',
    'huntEngine.packageStaged': '内容包已暂存。',
    'huntEngine.packageRemoved': '内容包已移除。',
    'huntEngine.packSent': '已发送当前资源包。',
    'huntEngine.choosePackage': '请先选择 ZIP 内容包。',
    'huntEngine.uploadTooLarge': '内容包不得超过 8 MiB。',
    'huntEngine.confirmOperation': '确认执行 {operation}？若操作失败，当前生效资源包不会被替换。',
    'huntEngine.confirmRemovePackage': '确认从暂存区移除此内容包？',
    'titles.eyebrow': '原生称号系统',
    'titles.title': '称号',
    'titles.manage': '称号定义',
    'titles.assign': '玩家分配',
    'titles.save': '保存称号',
    'titles.apply': '应用',
    'titles.moduleToggle': '启用称号模块',
    'titles.id': '称号 ID',
    'titles.idPlaceholder': '唯一 ID，例如 builder',
    'titles.idHint': '命令和配置使用的内部 ID，建议小写字母、数字、短横线或下划线。',
    'titles.displayName': '显示名',
    'titles.displayNamePlaceholder': '面板里看到的名称，例如 Builder',
    'titles.displayNameHint': '显示在网页面板和称号列表里的名称。',
    'titles.prefix': '聊天前缀',
    'titles.prefixPlaceholder': '支持 & 颜色，例如 &b[Builder] ',
    'titles.prefixHint': '显示在聊天、头顶名或 TAB 里的前缀文本。',
    'titles.priority': '优先级',
    'titles.priorityPlaceholder': '数字越大优先级越高',
    'titles.priorityHint': '玩家拥有多个可显示称号时，数字更大的优先生效。',
    'titles.permission': '权限节点',
    'titles.permissionPlaceholder': '留空或 huntercore.title.builder',
    'titles.permissionHint': '留空表示只靠手动发放；填写后玩家还需要拥有该权限。',
    'titles.description': '说明',
    'titles.descriptionPlaceholder': '这个称号的用途或发放条件',
    'titles.descriptionHint': '管理员备注，例如活动奖励、身份组或解锁条件。',
    'titles.assignPlayer': '玩家名',
    'titles.assignPlayerPlaceholder': '要发放/撤销称号的玩家名',
    'titles.assignTitle': '称号 ID',
    'titles.assignTitlePlaceholder': '例如 builder',
    'console.title': '命令控制台',
    'console.placeholder': 'list',
    'console.run': '运行',
    'action.run': '运行',
    'action.close': '关闭',
    'commandCenter.heal': '治疗',
    'commandCenter.fly': '飞行',
    'commandCenter.gamemode': '游戏模式',
    'commandCenter.speed': '速度',
    'commandCenter.broadcast': '广播',
    'commandCenter.ncr': '聊天举报保护',
    'commandCenter.optimize': '优化模式',
    'commandCenter.playerOptional': '玩家名，留空为自己',
    'commandCenter.message': '广播内容',
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
    'actors.aiFree': 'AI-Free 自由模式',
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
    'settings.tab.bundles': '内置插件',
    'settings.tab.bundlesHint': 'Geyser、HuntEngine 和兼容项',
    'settings.tab.geyser': 'Geyser',
    'settings.tab.geyserHint': '基岩版端口和登录模式',
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
    'access.tab.permissions': 'GUI / 权限',
    'access.tab.permissionsHint': '假人与功能入口权限',
    'permissions.title': 'GUI 与权限',
    'permissions.fakeChatEnabled': '假人聊天控制',
    'permissions.fakeAmbientEnabled': '允许自然聊天触发',
    'permissions.fakeRequirePermission': '控制假人需要权限',
    'permissions.fakePermission': '假人控制权限节点，例如 huntertools.ai.fakeplayer',
    'permissions.fakePrefix': '假人控制前缀，例如 @bot',
    'permissions.fakeCooldown': '假人控制冷却秒数',
    'permissions.guiPermissions': 'GUI / hc 功能权限',
    'permissions.default': '默认：{permission}',
    'permissions.save': '保存权限配置',
    'permissions.saved': '权限配置已保存。',
    'guiPermission.admin': '管理员 GUI / hc admin',
    'guiPermission.fake-players': '假人 GUI',
    'guiPermission.npcs': 'NPC GUI',
    'guiPermission.story': '剧情 GUI',
    'guiPermission.titles': '称号 GUI',
    'guiPermission.title-admin': '称号管理',
    'guiPermission.teleport': '传送 / TPA GUI',
    'guiPermission.homes': '家园 GUI',
    'guiPermission.random-teleport': '随机传送',
    'guiPermission.hunt-engine': 'HuntEngine 内容目录',
    'guiPermission.hunt-engine-give': 'HuntEngine 内容发放',
    'guiPermission.hunt-engine-admin': 'HuntEngine 管理',
    'guiPermission.auth': '登录认证 GUI',
    'guiPermission.tps': 'TPS 显示',
    'guiPermission.heal': '治疗',
    'guiPermission.feed': '饱食',
    'guiPermission.fly': '飞行',
    'guiPermission.gamemode': '游戏模式',
    'guiPermission.time': '时间',
    'guiPermission.weather': '天气',
    'guiPermission.broadcast': '广播',
    'guiPermission.clearchat': '清屏',
    'guiPermission.speed': '速度',
    'guiPermission.spawn': '出生点',
    'guiPermission.setspawn': '设置出生点',
    'guiPermission.back': '返回死亡/传送点',
    'guiPermission.hat': '帽子',
    'guiPermission.craft': '工作台',
    'guiPermission.enderchest': '末影箱',
    'guiPermission.trash': '垃圾桶',
    'guiPermission.menu': '主菜单 GUI',
    'guiPermission.profile': '个人资料 GUI',
    'guiPermission.settings': '设置 GUI',
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
    'bundles.title': '内置插件',
    'bundles.geyser': 'Geyser 跨平台',
    'bundles.floodgate': 'Floodgate 登录桥',
    'bundles.resourcePackEnabled': '启用资源包',
    'bundles.resourcePackRequired': '强制资源包',
    'bundles.sendOnJoin': '进服发送资源包',
    'bundles.resourcePackUrl': '资源包下载 URL',
    'bundles.resourcePackSha1': '资源包 SHA1，可留空',
    'noChatReports.title': '聊天举报保护',
    'noChatReports.enabled': '禁用玩家聊天举报',
    'noChatReports.addQueryData': '向客户端显示保护状态',
    'noChatReports.convertToGameMessage': '聊天转为游戏消息',
    'noChatReports.demandOnClient': '要求客户端安装 No Chat Reports',
    'noChatReports.debugLog': '调试日志',
    'noChatReports.disconnectMessage': '要求客户端 Mod 时的踢出提示',
    'noChatReports.disconnectMessagePlaceholder': '例如：请安装 No Chat Reports 后再进入服务器',
    'noChatReports.save': '保存聊天保护',
    'geyser.title': 'Geyser',
    'geyser.bedrockAddress': '基岩版监听地址',
    'geyser.bedrockAddressPlaceholder': '0.0.0.0 表示所有网卡',
    'geyser.bedrockPort': '基岩版端口',
    'geyser.bedrockPortPlaceholder': '默认 19132',
    'geyser.authType': 'Java 登录模式',
    'geyser.serverName': '基岩版服务器名',
    'geyser.serverNamePlaceholder': '基岩版服务器列表显示名',
    'geyser.primaryMotd': '主 MOTD',
    'geyser.primaryMotdPlaceholder': '第一行服务器描述',
    'geyser.secondaryMotd': '副 MOTD',
    'geyser.secondaryMotdPlaceholder': '第二行服务器描述',
    'geyser.passthroughMotd': '跟随 Java MOTD',
    'geyser.passthroughPlayers': '跟随 Java 在线人数',
    'geyser.openSettings': '打开 Geyser 配置',
    'geyser.save': '保存 Geyser 设置',
    'geyser.status': '配置：{config}；基岩地址：{address}:{port}；登录模式：{auth}。',
    'auth.title': 'HunterAuth',
    'auth.enabled': '登录保护',
    'auth.registrationRequired': '必须创建账号密码',
    'auth.webRegistrationRequired': '必须网页登录后才能进服',
    'auth.webRegistrationEnabled': '开放网页注册',
    'auth.webLoginEnabled': '允许玩家用游戏密码登录网页',
    'auth.guiEnabled': '登录 GUI',
    'auth.openGuiOnJoin': '进服打开登录 GUI',
    'auth.resourcePackGui': '资源包增强登录 GUI',
    'auth.resourcePackPromptOnJoin': '进服先请求界面资源包',
    'auth.minimumPasswordLength': '最小密码长度',
    'auth.loginTimeoutSeconds': '登录超时秒数（0 为关闭）',
    'auth.maxLoginAttempts': '最大输错次数',
    'auth.lockoutSeconds': '锁定秒数',
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
    'commandMessages.version': '/version',
    'commandMessages.rules': '/rules',
    'commandMessages.discord': '/discord',
    'commandMessages.website': '/website',
    'commandMessages.motd': '/motd',
    'commandMessages.info': '/info',
    'commandMessages.server': '/server',
    'commandMessages.links': '/links',
    'commandMessages.qq': '/qq',
    'commandMessages.group': '/group',
    'commandMessages.opDenied': '/op 无权限',
    'commandMessages.aboutPlaceholder': '&b"HunterCore" Server &8| &fPowered by &6HunterCore',
    'commandMessages.pluginsPlaceholder': '&6插件列表 &8| &f由管理员维护',
    'commandMessages.versionPlaceholder': '&6版本信息 &8| &fPowered by &bHunterCore',
    'commandMessages.rulesPlaceholder': '&6服务器规则\\n&71. 尊重其他玩家。',
    'commandMessages.discordPlaceholder': '&6Discord\\n&7填写你的邀请链接。',
    'commandMessages.websitePlaceholder': '&6网站\\n&7填写你的官网或公告页。',
    'commandMessages.motdPlaceholder': '&bHunterCore &8| &f欢迎来到服务器',
    'commandMessages.infoPlaceholder': '&b%server% &8| &f服务器信息\\n&7在线：&f%online%/%max%',
    'commandMessages.serverPlaceholder': '&b%server% &8| &f服务器介绍\\n&7填写玩法、版本和入口说明。',
    'commandMessages.linksPlaceholder': '&6服务器链接\\n&7官网：填写链接\\n&7地图：填写链接',
    'commandMessages.qqPlaceholder': '&6QQ群\\n&7填写群号或邀请方式。',
    'commandMessages.groupPlaceholder': '&6社群\\n&7填写玩家社群入口。',
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
    'ai.fakePlayersQuickResponse': '快速响应模式',
    'ai.quickOff': '关闭快速响应',
    'ai.quickLocked': '快速响应 + 去重锁',
    'ai.fakePlayersFreeWarning': '危险自由模式：只有 OP 可以用 /player spawn <name> -aifree 创建。该假人会像自主玩家一样自己观察、移动、操作、建造、聊天，并且可以执行服务器指令，不只是等待玩家命令。',
    'ai.fakePlayersMovement': '允许移动',
    'ai.fakePlayersBreaking': '允许挖掘',
    'ai.fakePlayersPlacing': '允许放置方块',
    'ai.fakePlayersInteraction': '允许交互/使用工具',
    'ai.fakePlayersChatControl': '聊天控制假人',
    'ai.fakePlayersAmbientChat': '无需点名也响应游戏/网页聊天',
    'ai.fakePlayersChatPrefix': '聊天控制前缀，如 @bot',
    'ai.fakePlayersChatCooldown': '聊天控制冷却秒',
    'ai.fakePlayersChatPermissionRequired': '需要权限',
    'ai.fakePlayersChatPermission': '聊天控制权限节点',
    'ai.fakePlayerPersonas': 'Fake player roleplay personas',
    'ai.addFakePlayerPersona': 'Add persona',
    'ai.personaName': 'AI name',
    'ai.personaAliases': 'Aliases',
    'ai.personaPrompt': 'Persona prompt',
    'ai.personaGoal': 'Default behavior / goal',
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
    'webUsers.identityRequired': '该玩家须先在游戏内完成一次 HunterAuth 登录，网页和游戏使用同一密码。',
    'webUsers.commands': '允许命令',
    'webUsers.allowedCommands': 'list spawn 或 *',
    'webUsers.save': '保存身份',
    'webUsers.saved': '网页身份已保存。',
    'webUsers.removed': '网页身份已移除。',
    'webUsers.none': '暂无网页身份配置。',
    'webUsers.identityBound': 'HunterAuth 已绑定',
    'webUsers.identityMissing': '等待 HunterAuth 绑定',
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
    'role.content-editor': '内容编辑者',
    'role.content-publisher': '内容发布者',
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
    'plugin.risk.protected': '核心/面板插件，禁止热操作',
    'plugin.risk.legacy': '旧外部插件，核心已内置替代',
    'plugin.risk.restart': '已安装未加载，通常需要加载或重启',
    'plugin.risk.runtime': '支持网页热操作，生产环境建议低峰操作',
    'plugin.dependencies': '依赖',
    'plugin.authors': '作者',
    'plugin.size': '大小',
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
    'nav.huntEngine': 'HuntEngine',
    'nav.titles': 'Titles',
    'nav.settings': 'Settings',
    'nav.access': 'Access',
    'nav.ai': 'AI',
    'nav.admin': 'Admin',
    'nav.more': 'More',
    'language.switch': 'Switch to Chinese',
    'session.eyebrow': 'Web console',
    'session.guest': 'Guest view',
    'login.username': 'Player name / panel admin',
    'login.password': 'Game password / panel password',
    'login.action': 'Login',
    'identity.claimRequired': 'This web account is not yet bound to a game UUID. Join the server and claim it with /login <web password>; on an online-mode server you can also claim it with /register <new password> <new password>. Chat and commands stay disabled until then.',
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
    'connection.action': 'Connection',
    'connection.eyebrow': 'Connection status',
    'connection.title': 'Backend connection',
    'connection.backendUrl': 'Backend URL',
    'connection.apiKey': 'API key',
    'connection.local': 'Local panel',
    'connection.connecting': 'Connecting',
    'connection.online': 'Connected',
    'connection.offline': 'Backend unavailable',
    'connection.stale': 'Data is stale',
    'connection.required': 'Backend URL required',
    'connection.updated': 'Updated {time}',
    'form.unsaved': 'You have unsaved changes. Background refresh will not overwrite this form.',
    'form.saving': 'Saving…',
    'remote.corsEnabled': 'CORS for standalone frontend',
    'remote.corsAllowOrigin': 'Allowed origin, e.g. * or https://panel.example.com',
    'remote.apiKeyEnabled': 'API key management',
    'remote.clearApiKey': 'Clear API key',
    'metric.online': 'Online',
    'metric.memory': 'Memory',
    'map.open': 'Open map in new tab',
    'map.stateEyebrow': 'Map status',
    'map.loadingTitle': 'Preparing map',
    'map.loadingDescription': 'Checking the map service…',
    'map.emptyTitle': 'Map is not connected',
    'map.emptyDescription': 'Connect a backend to load the map, or ask an administrator to check the map URL.',
    'map.errorTitle': 'Map is temporarily unavailable',
    'map.errorDescription': 'The map URL could not be read. Check the backend connection and try again.',
    'map.retry': 'Try again',
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
    'plugins.page': 'Page {page} / {pages} · {count} plugins',
    'plugins.prevPage': 'Previous',
    'plugins.nextPage': 'Next',
    'plugins.search': 'Search plugins, jars, descriptions or authors',
    'plugins.empty': 'No matching plugins.',
    'plugins.summary': 'Total {total} · Enabled {enabled} · Disabled {disabled} · Installed {installed} · Protected {protected}',
    'plugins.releaseReady': 'Release readiness',
    'plugins.releaseReadyOk': 'Plugin release status looks good',
    'plugins.releaseReadyWarn': '{count} plugin items need attention',
    'plugins.issue.missingDependency': 'Missing dependencies',
    'plugins.issue.installedOnly': 'Installed only',
    'plugins.issue.disabled': 'Disabled',
    'plugins.issue.legacyApi': 'Legacy API',
    'plugins.issue.legacyNoChatReports': 'Legacy external NoChatReports',
    'plugins.issue.noDescriptor': 'Descriptor missing',
    'plugins.legacyNoChatReports': 'Legacy external NoChatReports is replaced by HunterCore built-in chat report protection and can be removed.',
    'plugins.detail': 'Details',
    'plugins.filter.all': 'All',
    'plugins.filter.enabled': 'Enabled',
    'plugins.filter.disabled': 'Disabled',
    'plugins.filter.installed': 'Installed only',
    'plugins.filter.protected': 'Protected',
    'plugins.filter.actionable': 'Actionable',
    'worlds.none': 'No worlds loaded.',
    'players.none': 'No players online.',
    'tools.eyebrow': 'Minecraft actions',
    'tools.title': 'Tools',
    'huntEngine.eyebrow': 'Custom content operations',
    'huntEngine.title': 'HuntEngine',
    'huntEngine.catalogue': 'Content catalogue',
    'huntEngine.catalogueHint': 'The engine owns every content definition; the panel never writes engine YAML.',
    'huntEngine.packages': 'Native content packages',
    'huntEngine.packagesHint': 'Upload a native ZIP content package to HuntEngine staging.',
    'huntEngine.simpleItem': 'Safe simple-item wizard',
    'huntEngine.simpleItemHint': 'Creates one vanilla-backed item in the fixed huntercraft namespace. Models, blocks, furniture, recipes, fonts, images, and sounds still use reviewed native ZIP packages.',
    'huntEngine.simpleItemId': 'Item ID',
    'huntEngine.simpleItemIdPlaceholder': 'starter_token',
    'huntEngine.simpleItemIdHint': 'Fixed content ID: huntercraft:<id>. Use lowercase letters, digits, _ or -.',
    'huntEngine.simpleItemMaterial': 'Vanilla material',
    'huntEngine.simpleItemMaterialHint': 'Only the wizard\'s safe Bukkit-material allowlist is available.',
    'huntEngine.simpleItemName': 'Display name',
    'huntEngine.simpleItemNamePlaceholder': 'Starter Token',
    'huntEngine.simpleItemDescription': 'Description',
    'huntEngine.simpleItemDescriptionPlaceholder': 'A small welcome gift.',
    'huntEngine.simpleItemTextHint': 'Plain text only; formatting, templates, and click actions are not accepted.',
    'huntEngine.createSimpleItem': 'Create and stage item',
    'huntEngine.simpleItemStaged': 'Simple item {id} was created and staged. Validate, build, and publish it before use.',
    'huntEngine.upload': 'Stage package',
    'huntEngine.operations': 'Build and publish',
    'huntEngine.operationsHint': 'Build, publish, and reload run asynchronously. A failed operation never replaces the active pack.',
    'huntEngine.validate': 'Validate',
    'huntEngine.build': 'Build',
    'huntEngine.publish': 'Publish',
    'huntEngine.reload': 'Reload',
    'huntEngine.sendPackPlayer': 'Online player',
    'huntEngine.sendPack': 'Send active pack',
    'huntEngine.migration': 'Migration diagnostics',
    'huntEngine.settingsManaged': 'The HuntEngine workspace owns resource-pack lifecycle.',
    'huntEngine.contents': 'Content',
    'huntEngine.staged': 'Staged packages',
    'huntEngine.resourcePack': 'Resource pack',
    'huntEngine.migrationSummary': 'Migration',
    'huntEngine.available': 'Engine available',
    'huntEngine.unavailable': 'Engine unavailable',
    'huntEngine.packPublished': 'Active resource pack published',
    'huntEngine.packConfigured': 'Resource pack configured; publish pending',
    'huntEngine.packMissing': 'No active resource pack',
    'huntEngine.noContents': 'No content in the catalogue yet.',
    'huntEngine.noPackages': 'The staging area is empty.',
    'huntEngine.removePackage': 'Remove',
    'huntEngine.noOperations': 'No operations have started yet.',
    'huntEngine.noMigration': 'No migration records yet.',
    'huntEngine.operationStarted': 'HuntEngine operation started.',
    'huntEngine.packageStaged': 'Content package staged.',
    'huntEngine.packageRemoved': 'Content package removed.',
    'huntEngine.packSent': 'Active resource pack sent.',
    'huntEngine.choosePackage': 'Choose a ZIP content package first.',
    'huntEngine.uploadTooLarge': 'Content packages must not exceed 8 MiB.',
    'huntEngine.confirmOperation': 'Run {operation}? A failed operation will not replace the active resource pack.',
    'huntEngine.confirmRemovePackage': 'Remove this content package from staging?',
    'titles.eyebrow': 'Native title system',
    'titles.title': 'Titles',
    'titles.manage': 'Title Definitions',
    'titles.assign': 'Player Assignments',
    'titles.save': 'Save title',
    'titles.apply': 'Apply',
    'titles.moduleToggle': 'Enable titles module',
    'titles.id': 'Title ID',
    'titles.idPlaceholder': 'Unique ID, e.g. builder',
    'titles.idHint': 'Internal ID for commands and config; use lowercase letters, numbers, dash or underscore.',
    'titles.displayName': 'Display name',
    'titles.displayNamePlaceholder': 'Panel name, e.g. Builder',
    'titles.displayNameHint': 'Name shown in the web panel and title list.',
    'titles.prefix': 'Chat prefix',
    'titles.prefixPlaceholder': 'Supports & colors, e.g. &b[Builder] ',
    'titles.prefixHint': 'Prefix text shown in chat, nametag, or tab.',
    'titles.priority': 'Priority',
    'titles.priorityPlaceholder': 'Higher numbers win first',
    'titles.priorityHint': 'When a player has multiple displayable titles, the higher number wins.',
    'titles.permission': 'Permission node',
    'titles.permissionPlaceholder': 'Blank or huntercore.title.builder',
    'titles.permissionHint': 'Leave blank for manual grants only, or require this permission.',
    'titles.description': 'Description',
    'titles.descriptionPlaceholder': 'When this title should be used',
    'titles.descriptionHint': 'Short admin note, such as event reward, staff rank, or unlock condition.',
    'titles.assignPlayer': 'Player name',
    'titles.assignPlayerPlaceholder': 'Player to grant or revoke from',
    'titles.assignTitle': 'Title ID',
    'titles.assignTitlePlaceholder': 'e.g. builder',
    'console.title': 'Command console',
    'console.placeholder': 'list',
    'console.run': 'Run',
    'action.run': 'Run',
    'action.close': 'Close',
    'commandCenter.heal': 'Heal',
    'commandCenter.fly': 'Fly',
    'commandCenter.gamemode': 'Gamemode',
    'commandCenter.speed': 'Speed',
    'commandCenter.broadcast': 'Broadcast',
    'commandCenter.ncr': 'Chat Reports',
    'commandCenter.optimize': 'Optimize',
    'commandCenter.playerOptional': 'Player name, blank for self',
    'commandCenter.message': 'Broadcast message',
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
    'actors.aiFree': 'AI-Free autonomous mode',
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
    'settings.tab.bundles': 'Bundled plugins',
    'settings.tab.bundlesHint': 'Geyser, HuntEngine and compatibility',
    'settings.tab.geyser': 'Geyser',
    'settings.tab.geyserHint': 'Bedrock port and login mode',
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
    'access.tab.permissions': 'GUI / Permissions',
    'access.tab.permissionsHint': 'Playerbot and GUI access',
    'permissions.title': 'GUI and permissions',
    'permissions.fakeChatEnabled': 'Playerbot chat control',
    'permissions.fakeAmbientEnabled': 'Ambient chat trigger',
    'permissions.fakeRequirePermission': 'Require permission to control playerbots',
    'permissions.fakePermission': 'Playerbot control permission, e.g. huntertools.ai.fakeplayer',
    'permissions.fakePrefix': 'Playerbot control prefix, e.g. @bot',
    'permissions.fakeCooldown': 'Playerbot control cooldown seconds',
    'permissions.guiPermissions': 'GUI / hc feature permissions',
    'permissions.default': 'Default: {permission}',
    'permissions.save': 'Save permissions',
    'permissions.saved': 'Permission settings saved.',
    'guiPermission.admin': 'Admin GUI / hc admin',
    'guiPermission.fake-players': 'Playerbots GUI',
    'guiPermission.npcs': 'NPC GUI',
    'guiPermission.story': 'Story GUI',
    'guiPermission.titles': 'Titles GUI',
    'guiPermission.title-admin': 'Title administration',
    'guiPermission.teleport': 'TPA / teleport GUI',
    'guiPermission.homes': 'Homes GUI',
    'guiPermission.random-teleport': 'Random teleport',
    'guiPermission.hunt-engine': 'HuntEngine catalogue',
    'guiPermission.hunt-engine-give': 'HuntEngine content give',
    'guiPermission.hunt-engine-admin': 'HuntEngine administration',
    'guiPermission.auth': 'Auth GUI',
    'guiPermission.tps': 'TPS display',
    'guiPermission.heal': 'Heal',
    'guiPermission.feed': 'Feed',
    'guiPermission.fly': 'Fly',
    'guiPermission.gamemode': 'Game mode',
    'guiPermission.time': 'Time',
    'guiPermission.weather': 'Weather',
    'guiPermission.broadcast': 'Broadcast',
    'guiPermission.clearchat': 'Clear chat',
    'guiPermission.speed': 'Speed',
    'guiPermission.spawn': 'Spawn',
    'guiPermission.setspawn': 'Set spawn',
    'guiPermission.back': 'Back',
    'guiPermission.hat': 'Hat',
    'guiPermission.craft': 'Crafting table',
    'guiPermission.enderchest': 'Ender chest',
    'guiPermission.trash': 'Trash',
    'guiPermission.menu': 'Main GUI',
    'guiPermission.profile': 'Profile GUI',
    'guiPermission.settings': 'Settings GUI',
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
    'bundles.title': 'Bundled plugins',
    'bundles.geyser': 'Geyser cross-platform',
    'bundles.floodgate': 'Floodgate login bridge',
    'bundles.resourcePackEnabled': 'Enable resource pack',
    'bundles.resourcePackRequired': 'Require resource pack',
    'bundles.sendOnJoin': 'Send pack on join',
    'bundles.resourcePackUrl': 'Resource pack download URL',
    'bundles.resourcePackSha1': 'Resource pack SHA1, optional',
    'bundles.ncrLine': 'Built-in NoChatReports: {builtin}. This is handled by HunterCore directly.',
    'noChatReports.title': 'Chat report protection',
    'noChatReports.enabled': 'Disable player chat reports',
    'noChatReports.addQueryData': 'Advertise protection to clients',
    'noChatReports.convertToGameMessage': 'Convert chat to game messages',
    'noChatReports.demandOnClient': 'Require client No Chat Reports mod',
    'noChatReports.debugLog': 'Debug log',
    'noChatReports.disconnectMessage': 'Kick message when client mod is required',
    'noChatReports.disconnectMessagePlaceholder': 'Example: install No Chat Reports before joining',
    'noChatReports.save': 'Save chat protection',
    'geyser.title': 'Geyser',
    'geyser.bedrockAddress': 'Bedrock bind address',
    'geyser.bedrockAddressPlaceholder': '0.0.0.0 listens on all interfaces',
    'geyser.bedrockPort': 'Bedrock port',
    'geyser.bedrockPortPlaceholder': 'Default 19132',
    'geyser.authType': 'Java login mode',
    'geyser.serverName': 'Bedrock server name',
    'geyser.serverNamePlaceholder': 'Name shown in the Bedrock server list',
    'geyser.primaryMotd': 'Primary MOTD',
    'geyser.primaryMotdPlaceholder': 'First server description line',
    'geyser.secondaryMotd': 'Secondary MOTD',
    'geyser.secondaryMotdPlaceholder': 'Second server description line',
    'geyser.passthroughMotd': 'Passthrough Java MOTD',
    'geyser.passthroughPlayers': 'Passthrough Java player counts',
    'geyser.openSettings': 'Open Geyser settings',
    'geyser.save': 'Save Geyser settings',
    'geyser.status': 'Config: {config}; Bedrock address: {address}:{port}; login mode: {auth}.',
    'auth.title': 'HunterAuth',
    'auth.enabled': 'Login protection',
    'auth.registrationRequired': 'Require account password',
    'auth.webRegistrationRequired': 'Require web pre-registration',
    'auth.webRegistrationEnabled': 'Open web registration',
    'auth.webLoginEnabled': 'Allow players to web-login with game password',
    'auth.guiEnabled': 'Login GUI',
    'auth.openGuiOnJoin': 'Open GUI on join',
    'auth.resourcePackGui': 'Resource-pack login GUI',
    'auth.resourcePackPromptOnJoin': 'Ask for UI pack on join',
    'auth.minimumPasswordLength': 'Minimum password length',
    'auth.loginTimeoutSeconds': 'Login timeout seconds (0 disables)',
    'auth.maxLoginAttempts': 'Maximum wrong attempts',
    'auth.lockoutSeconds': 'Lockout seconds',
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
    'commandMessages.version': '/version',
    'commandMessages.rules': '/rules',
    'commandMessages.discord': '/discord',
    'commandMessages.website': '/website',
    'commandMessages.motd': '/motd',
    'commandMessages.info': '/info',
    'commandMessages.server': '/server',
    'commandMessages.links': '/links',
    'commandMessages.qq': '/qq',
    'commandMessages.group': '/group',
    'commandMessages.opDenied': '/op denied',
    'commandMessages.aboutPlaceholder': '&b"HunterCore" Server &8| &fPowered by &6HunterCore',
    'commandMessages.pluginsPlaceholder': '&6Plugin list &8| &fManaged by staff',
    'commandMessages.versionPlaceholder': '&6Version &8| &fPowered by &bHunterCore',
    'commandMessages.rulesPlaceholder': '&6Server rules\\n&71. Respect other players.',
    'commandMessages.discordPlaceholder': '&6Discord\\n&7Set your invite link here.',
    'commandMessages.websitePlaceholder': '&6Website\\n&7Set your website or announcement link here.',
    'commandMessages.motdPlaceholder': '&bHunterCore &8| &fWelcome to the server',
    'commandMessages.infoPlaceholder': '&b%server% &8| &fServer info\\n&7Online: &f%online%/%max%',
    'commandMessages.serverPlaceholder': '&b%server% &8| &fServer intro\\n&7Set gameplay, version, and entry notes.',
    'commandMessages.linksPlaceholder': '&6Server links\\n&7Website: set link\\n&7Map: set link',
    'commandMessages.qqPlaceholder': '&6QQ group\\n&7Set group number or invite instructions.',
    'commandMessages.groupPlaceholder': '&6Community\\n&7Set player community entry.',
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
    'ai.fakePlayersQuickResponse': 'Quick response mode',
    'ai.quickOff': 'Quick response off',
    'ai.quickLocked': 'Quick response + duplicate lock',
    'ai.fakePlayersFreeWarning': 'Dangerous autonomous mode: OPs can create it with /player spawn <name> -aifree. The bot observes, moves, acts, builds, chats, and may run server commands by itself; it is not merely waiting for player instructions.',
    'ai.fakePlayersMovement': 'allow movement',
    'ai.fakePlayersBreaking': 'allow breaking',
    'ai.fakePlayersPlacing': 'allow block placing',
    'ai.fakePlayersInteraction': 'allow interaction/tools',
    'ai.fakePlayersChatControl': 'chat control',
    'ai.fakePlayersAmbientChat': 'respond to ambient game/web chat',
    'ai.fakePlayersChatPrefix': 'Chat control prefix, e.g. @bot',
    'ai.fakePlayersChatCooldown': 'Chat control cooldown seconds',
    'ai.fakePlayersChatPermissionRequired': 'require permission',
    'ai.fakePlayersChatPermission': 'Chat control permission node',
    'ai.chatProfiles': 'AI Chat profiles',
    'ai.addProfile': 'Add profile',
    'ai.botAliases': 'AI Bot aliases',
    'ai.addBotAlias': 'Add bot',
    'ai.fakePlayerPersonas': 'Fake player roleplay personas',
    'ai.addFakePlayerPersona': 'Add persona',
    'ai.profileName': 'Profile name',
    'ai.profileAliases': 'Trigger names / aliases, comma separated',
    'ai.profileFormat': 'Response format, e.g. &b%name% &8> &f%response%',
    'ai.profilePrompt': 'Profile prompt',
    'ai.botTarget': 'Fake player name',
    'ai.botAliasNames': 'Trigger names / aliases, comma separated',
    'ai.personaName': 'AI name',
    'ai.personaAliases': 'Aliases',
    'ai.personaPrompt': 'Persona prompt',
    'ai.personaGoal': 'Default behavior / goal',
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
    'webUsers.identityRequired': 'The player must complete one in-game HunterAuth login; web and game use the same password.',
    'webUsers.commands': 'commands',
    'webUsers.allowedCommands': 'list spawn or *',
    'webUsers.save': 'Save role',
    'webUsers.saved': 'Web role saved.',
    'webUsers.removed': 'Web role removed.',
    'webUsers.none': 'No web roles configured.',
    'webUsers.identityBound': 'HunterAuth bound',
    'webUsers.identityMissing': 'HunterAuth binding required',
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
    'role.content-editor': 'Content editor',
    'role.content-publisher': 'Content publisher',
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
    'plugin.risk.protected': 'Core/panel plugin; hot operations are blocked',
    'plugin.risk.legacy': 'Legacy external plugin replaced by built-in core behavior',
    'plugin.risk.restart': 'Installed but not loaded; load or restart may be required',
    'plugin.risk.runtime': 'Web hot operation available; prefer quiet production windows',
    'plugin.dependencies': 'Dependencies',
    'plugin.authors': 'Authors',
    'plugin.size': 'Size',
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
  const normalized = String(role || '').toLowerCase();
  return t(`role.${['admin', 'player', 'content-editor', 'content-publisher'].includes(normalized) ? normalized : 'guest'}`);
}

function statusLabel(status) {
  return t(`status.${status || 'ok'}`);
}

function pluginStatusLabel(status) {
  return t(`plugin.status.${status || 'disabled'}`);
}

function guiPermissionLabel(key, fallback) {
  const label = t(`guiPermission.${key}`);
  return label === `guiPermission.${key}` ? fallback : label;
}

function pluginRiskLabel(plugin) {
  return t(`plugin.risk.${plugin.risk || (plugin.controllable ? 'runtime' : 'protected')}`);
}

function formatBytes(bytes) {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value < 0) return '';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`;
  return `${(value / 1024 / 1024).toFixed(1)} MiB`;
}

function safeExternalUrl(value) {
  try {
    const url = new URL(String(value || ''));
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : '';
  } catch {
    return '';
  }
}

function pluginMatches(plugin, query, filter) {
  const loaded = plugin.loaded !== false;
  const enabled = Boolean(plugin.enabled);
  const status = plugin.status || (loaded ? (enabled ? 'enabled' : 'disabled') : 'installed');
  const protectedPlugin = !plugin.controllable;
  if (filter === 'enabled' && status !== 'enabled') return false;
  if (filter === 'disabled' && status !== 'disabled') return false;
  if (filter === 'installed' && status !== 'installed') return false;
  if (filter === 'protected' && !protectedPlugin) return false;
  if (filter === 'actionable' && protectedPlugin) return false;
  if (!query) return true;
  const haystack = [
    plugin.name,
    plugin.version,
    plugin.sourceJar,
    plugin.descriptor,
    plugin.main,
    plugin.description,
    plugin.website,
    ...(plugin.authors || []),
    ...(plugin.dependencies || []),
    ...(plugin.softDependencies || [])
  ].join(' ').toLowerCase();
  return haystack.includes(query);
}

function pluginSummaryLine(plugins) {
  const summary = (plugins || []).reduce((acc, plugin) => {
    acc.total += 1;
    if (plugin.status === 'installed' || plugin.loaded === false) acc.installed += 1;
    else if (plugin.enabled) acc.enabled += 1;
    else acc.disabled += 1;
    if (!plugin.controllable) acc.protected += 1;
    return acc;
  }, { total: 0, enabled: 0, disabled: 0, installed: 0, protected: 0 });
  return t('plugins.summary', summary);
}

function pluginReadinessIssues(plugins) {
  const names = new Set((plugins || []).map((plugin) => String(plugin.name || '').toLowerCase()));
  const buckets = {
    missingDependency: [],
    installedOnly: [],
    disabled: [],
    legacyApi: [],
    legacyNoChatReports: [],
    noDescriptor: []
  };
  (plugins || []).forEach((plugin) => {
    const missing = (plugin.dependencies || []).filter((dependency) => !names.has(String(dependency).toLowerCase()));
    if (missing.length) buckets.missingDependency.push(`${plugin.name}: ${missing.join(', ')}`);
    if (plugin.loaded === false || plugin.status === 'installed') buckets.installedOnly.push(plugin.name);
    else if (!plugin.enabled) buckets.disabled.push(plugin.name);
    if (plugin.apiVersion && /^1\.(1[0-9]|20)(\.|$)/.test(plugin.apiVersion)) buckets.legacyApi.push(`${plugin.name}: ${plugin.apiVersion}`);
    if (plugin.legacyNoChatReports) buckets.legacyNoChatReports.push(plugin.name);
    if (!plugin.descriptor) buckets.noDescriptor.push(plugin.name);
  });
  return buckets;
}

function pluginReadinessPanel(plugins) {
  if (!plugins) return '';
  const issues = pluginReadinessIssues(plugins);
  const issueCount = Object.values(issues).reduce((total, items) => total + items.length, 0);
  const cards = Object.entries(issues)
    .filter(([, items]) => items.length)
    .map(([key, items]) => `<details class="pluginReadinessCard" open>
      <summary><strong>${esc(t(`plugins.issue.${key}`))}</strong><span>${items.length}</span></summary>
      <p>${esc(items.slice(0, 8).join(' · '))}${items.length > 8 ? ' …' : ''}</p>
    </details>`)
    .join('');
  return `<div class="pluginReadinessHead">
    <span>${esc(t('plugins.releaseReady'))}</span>
    <strong class="${issueCount ? 'warningText' : 'okText'}">${esc(issueCount ? t('plugins.releaseReadyWarn', { count: issueCount }) : t('plugins.releaseReadyOk'))}</strong>
  </div>${cards || `<p class="mutedState">${esc(t('plugins.releaseReadyOk'))}</p>`}`;
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

function formForControl(control) {
  if (!(control instanceof Element)) return null;
  if (control instanceof HTMLInputElement || control instanceof HTMLSelectElement || control instanceof HTMLTextAreaElement || control instanceof HTMLButtonElement) {
    if (control.form) return control.form;
  }
  return control.closest('form');
}

function formIsDirty(formOrId) {
  const form = typeof formOrId === 'string' ? $(formOrId) : formOrId;
  return form instanceof HTMLFormElement && form.dataset.dirty === 'true';
}

function anyFormDirty(formIds) {
  return formIds.some((id) => formIsDirty(id));
}

function statusHostForForm(form, source) {
  if (!(form instanceof HTMLFormElement)) return null;
  const origin = source instanceof Element ? source : null;
  return origin?.closest('.panel, .connectionPanel, .authDialog') || form.closest('.panel, .connectionPanel, .authDialog') || form;
}

function setFormState(form, message, tone = '', source = null) {
  const host = statusHostForForm(form, source);
  if (!host || !form?.id) return;
  let line = host.querySelector(`.formState[data-form-state-for="${form.id}"]`);
  if (!line) {
    line = document.createElement('p');
    line.className = 'formState';
    line.dataset.formStateFor = form.id;
    line.setAttribute('role', 'status');
    line.setAttribute('aria-live', 'polite');
    host.append(line);
  }
  line.textContent = message || '';
  if (tone) line.dataset.tone = tone;
  else delete line.dataset.tone;
}

function markFormDirty(form, source = null) {
  if (!(form instanceof HTMLFormElement) || form.dataset.transient === 'true') return;
  form.dataset.dirty = 'true';
  setFormState(form, t('form.unsaved'), 'saving', source);
}

function markFormClean(form, message = '', tone = 'success', source = null) {
  if (!(form instanceof HTMLFormElement)) return;
  delete form.dataset.dirty;
  delete form.dataset.submitting;
  form.removeAttribute('aria-busy');
  if (message) setFormState(form, message, tone, source);
}

function setConnectionPanel(open, focus = false) {
  const panel = $('connectionPanel');
  const toggle = $('connectionToggle');
  if (!panel || !toggle) return;
  panel.hidden = !open;
  toggle.setAttribute('aria-expanded', String(open));
  if (focus && open) window.setTimeout(() => $('backendUrl')?.focus(), 0);
}

function setConnectionStatus(status, detail = '') {
  const safeStatus = ['idle', 'connecting', 'online', 'offline', 'stale'].includes(status) ? status : 'idle';
  state.connection.status = safeStatus;
  state.connection.detail = detail || '';
  const toggle = $('connectionToggle');
  const line = $('connectionFormState');
  const key = safeStatus === 'idle'
    ? (standaloneFrontend() && !state.backendUrl ? 'connection.required' : 'connection.local')
    : `connection.${safeStatus}`;
  const label = t(key);
  const detailLine = detail ? `${label} · ${detail}` : label;
  if (toggle) {
    toggle.dataset.state = safeStatus;
    toggle.setAttribute('aria-label', detailLine);
  }
  if (line) {
    line.textContent = detailLine;
    line.dataset.tone = safeStatus === 'offline' ? 'error' : safeStatus === 'online' ? 'success' : safeStatus === 'connecting' || safeStatus === 'stale' ? 'saving' : '';
  }
}

function renderBackendConnection() {
  const line = $('backendLine');
  if (!line) return;
  const form = $('connectionForm');
  const clearButton = $('clearConnectionButton');
  if (form) form.hidden = false;
  if (clearButton) clearButton.hidden = !state.backendUrl;
  line.textContent = state.backendUrl
    ? t('remote.connected', { url: state.backendUrl })
    : standaloneFrontend() ? t('remote.required') : t('remote.local');
  if (!formIsDirty(form)) {
    if ($('backendUrl')) $('backendUrl').value = state.backendUrl;
    if ($('backendApiKey')) $('backendApiKey').value = state.apiKey;
  }
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
  const activeForm = state.activeForm;
  if (activeForm instanceof HTMLFormElement && activeForm.isConnected) {
    const failed = /(?:error|failed|错误|失败)/i.test(String(message));
    markFormClean(activeForm, message, failed ? 'error' : 'success');
    state.activeForm = null;
  }
  showToast(message);
}

function setCommandPlaceholder() {
  $('commandResult').dataset.placeholder = 'true';
  $('commandResult').textContent = t('command.loginRequired');
}

function dataItem(left, right = '', meta = '') {
  return `<article class="dataItem workbenchCard">
    <div class="cardCopy">
      <span>${esc(left)}</span>
      ${meta ? `<small>${esc(meta)}</small>` : ''}
    </div>
    <strong>${esc(right)}</strong>
  </article>`;
}

function summaryCard(label, value, meta = '', tone = 'neutral') {
  return `<article class="summaryCard summaryCard--${esc(tone)}">
    <small>${esc(label)}</small>
    <strong>${esc(value)}</strong>
    ${meta ? `<span>${esc(meta)}</span>` : ''}
  </article>`;
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

function applyFieldHelp() {
  Object.entries(FIELD_HELP).forEach(([id, help]) => {
    const field = $(id);
    if (!field || !help) return;
    field.setAttribute('title', help);
    field.setAttribute('aria-description', help);
    const label = field.closest('label');
    if (label) label.setAttribute('title', help);
  });
}

function ensureAccessibleLabels(root = document) {
  const controls = root.querySelectorAll?.('input, select, textarea') || [];
  controls.forEach((control) => {
    if (control.type === 'hidden' || control.hasAttribute('aria-label') || control.hasAttribute('aria-labelledby') || control.labels?.length) return;
    const label = control.placeholder || control.name || control.id || 'Input';
    control.setAttribute('aria-label', label);
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
  translateOptions('webUserRole', {
    player: roleLabel('player'),
    'content-editor': roleLabel('content-editor'),
    'content-publisher': roleLabel('content-publisher'),
    admin: roleLabel('admin')
  });
  translateOptions('webUserAllowedMode', {
    inherit: t('allowed.inherit'),
    custom: t('allowed.custom'),
    none: t('allowed.none')
  });
  applyFieldHelp();
  ensureAccessibleLabels();
  translateOptions('actorModule', { npcs: t('actors.npc'), 'fake-players': t('actors.fakePlayer'), 'real-fake-players': t('actors.realFakePlayer') });
  translateOptions('actorKind', { villager: t('actors.villager'), mannequin: t('actors.mannequin') });
  const commandResult = $('commandResult');
  if (commandResult?.dataset.placeholder !== 'false') setCommandPlaceholder();
  renderBackendConnection();
  setConnectionStatus(state.connection.status, state.connection.detail);
  if (state.lastData?.auth) renderAuthPublic(state.lastData.auth);
}

function rerenderCachedStatus() {
  updateSessionChrome();
  if (!state.lastData) return;
  const data = state.lastData;
  renderHealth(data.health);
  renderOverview(data);
  renderHuntEngine(data.huntEngine);
  renderTitles(data.titles);
  renderActorWorlds(data.worlds);
  renderActors(data.actorDetails);
  renderOperations(data.modules);
  renderWebUsers(data.webUsers);
  renderWebSettings(data.webSettings);
  renderCommandMessages(data.commandMessages);
  renderAiApprovals(data.aiApprovals);
  renderAiSettings(data.aiSettings);
  renderPermissionSettings(data.permissionSettings);
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
  const playerOnly = page === 'tools';
  const huntEngineOnly = HUNT_ENGINE_PAGES.includes(page);
  const targetPage = ADMIN_PAGES.includes(page) && !state.session?.admin
    ? 'overview'
    : playerOnly && !state.session
      ? 'overview'
      : huntEngineOnly && !hasHuntEngineCapability('read')
        ? 'overview'
      : page;
  state.page = targetPage;
  $$('.pageView').forEach((view) => {
    const active = view.id === targetPage;
    view.hidden = !active;
    view.classList.toggle('isActive', active);
  });
  $$('[data-page-target]').forEach((button) => {
    button.classList.toggle('isActive', button.dataset.pageTarget === targetPage);
  });
  if (push && window.location.hash !== `#${targetPage}`) {
    history.pushState(null, '', `#${targetPage}`);
  }
  closeNavigationMenu();
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
  const aiFreeMeta = actor.module === 'real-fake-players' && actor.aiFree
    ? ` · ${esc(t('actors.aiFree'))}`
    : '';
  const metaLine = actor.module === 'real-fake-players'
    ? `${stateLabel} · ${moduleLabel} · ${esc(actor.pose || 'survival')} · ${esc(t('actors.loops'))}: ${esc(actor.loops || 'none')} · ${esc(location)} · ${esc(t('actors.clickCommand'))}: ${esc(clickLine)}${aiMeta}${aiStatusMeta}${aiFreeMeta}`
    : `${stateLabel} · ${moduleLabel} · ${esc(actor.kind)} · ${esc(t('actors.pose'))}: ${esc(actor.pose || 'standing')} · ${esc(location)} · ${esc(t('actors.clickCommand'))}: ${esc(clickLine)}${aiMeta}`;
  const aiLabel = actor.module === 'real-fake-players' ? t('actors.aiGoal') : t('actors.aiPersona');
  const aiPlaceholder = actor.module === 'real-fake-players' ? t('actors.aiGoalPlaceholder') : t('actors.aiPersonaPlaceholder');
  const actorAiControls = aiCapable
    ? `<label class="checkLine actorAiToggle"><input type="checkbox" data-actor-ai-enabled="true" ${actor.aiEnabled ? 'checked' : ''}> <span>${esc(t('actors.aiEnabled'))}</span></label>
      <textarea class="actorPersonaInput" rows="2" data-actor-ai-persona="true" aria-label="${esc(aiLabel)}" placeholder="${esc(aiPlaceholder)}">${esc(actor.aiPersona || '')}</textarea>
      <button type="button" data-actor-ai-save="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">${esc(t('actors.saveAi'))}</button>`
    : '';
  return `<article class="dataItem actorCard ${state.selectedActor === actor.id ? 'isSelected' : ''}" data-actor-select="${esc(actor.id)}">
    <span>${esc(actor.displayName)}<small>${metaLine}</small></span>
    <div class="actorActions">
      <input class="actorCommandInput" value="${esc(clickCommand)}" placeholder="${esc(t('actors.clickPlaceholder'))}" data-actor-command-input="true">
      <button type="button" data-actor-click-save="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">${esc(t('actors.saveClick'))}</button>
      <button type="button" data-actor-click-clear="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">${esc(t('actors.clearClick'))}</button>
      ${actorAiControls}
      <button type="button" data-actor-remove="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">${esc(t('action.remove'))}</button>
    </div>
  </article>`;
}

function allowedLine(user) {
  if (!user.allowedCommandsConfigured) return t('allowed.inherit');
  return user.allowedCommands?.length ? user.allowedCommands.join(', ') : t('allowed.none');
}

function webUserLine(user) {
  return `<article class="dataItem accessCard ${state.selectedWebUser === user.id ? 'isSelected' : ''}" data-user-select="${esc(user.id)}">
    <span>${esc(user.displayName)}<small>${esc(roleLabel(user.role))} · ${user.identityBound ? t('webUsers.identityBound') : t('webUsers.identityMissing')} · ${user.commandExecution ? t('webUsers.commandsOn') : t('webUsers.commandsOff')} · ${esc(allowedLine(user))}</small></span>
    <span class="userActions">
      <button type="button" data-user-edit="${esc(user.id)}">${esc(t('action.edit'))}</button>
      <button type="button" data-user-remove="${esc(user.id)}">${esc(t('action.remove'))}</button>
    </span>
  </article>`;
}

function aiApprovalLine(approval) {
  const who = approval.requestedBy ? approval.requestedBy : '--';
  return `<article class="dataItem accessCard">
    <span>${esc(approval.fakePlayerName)}<small>${esc(approval.label)} · ${esc(approval.detail)} · ${esc(who)} · ${approval.expiresInSeconds}s</small></span>
    <span class="userActions">
      <button type="button" data-ai-approval="${esc(approval.fakePlayerName)}" data-ai-action="approve">${esc(t('ai.approve'))}</button>
      <button type="button" data-ai-approval="${esc(approval.fakePlayerName)}" data-ai-action="deny">${esc(t('ai.deny'))}</button>
    </span>
  </article>`;
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
  const detail = [
    plugin.legacyNoChatReports ? t('plugins.legacyNoChatReports') : '',
    pluginRiskLabel(plugin),
    plugin.apiVersion ? `API ${plugin.apiVersion}` : '',
    plugin.fileSizeBytes >= 0 ? `${t('plugin.size')}: ${formatBytes(plugin.fileSizeBytes)}` : '',
    plugin.authors?.length ? `${t('plugin.authors')}: ${plugin.authors.join(', ')}` : '',
    plugin.dependencies?.length ? `${t('plugin.dependencies')}: ${plugin.dependencies.join(', ')}` : '',
    plugin.description || ''
  ].filter(Boolean).join(' · ');
  const website = safeExternalUrl(plugin.website);
  if (!admin) {
    return dataItem(plugin.name, pluginStatusLabel(status), [meta, detail].filter(Boolean).join(' · '));
  }
  const controlDisabled = controllable ? '' : 'disabled';
  const reloadDisabled = controllable && loaded ? '' : 'disabled';
  const updateDisabled = updateable ? '' : 'disabled';
  return `<div class="pluginItem ${plugin.legacyNoChatReports ? 'isLegacyNcr' : status === 'enabled' ? 'isEnabled' : status === 'installed' ? 'isInstalled' : 'isDisabled'}">
    <div class="pluginTop">
      <span>${esc(plugin.name)}<small>${esc(meta)}</small></span>
      <strong class="stateChip ${statusClass}">${esc(pluginStatusLabel(status))}</strong>
    </div>
    <p class="pluginRisk">${esc(detail || pluginRiskLabel(plugin))}</p>
    <details class="pluginDetails">
      <summary>${esc(t('plugins.detail'))}</summary>
      <dl>
        <dt>main</dt><dd>${esc(plugin.main || '-')}</dd>
        <dt>website</dt><dd>${website ? `<a href="${esc(website)}" target="_blank" rel="noreferrer">${esc(website)}</a>` : '-'}</dd>
        <dt>depend</dt><dd>${esc((plugin.dependencies || []).join(', ') || '-')}</dd>
        <dt>softdepend</dt><dd>${esc((plugin.softDependencies || []).join(', ') || '-')}</dd>
      </dl>
    </details>
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

function setModalBackgroundInert(active) {
  const app = document.querySelector('.app');
  if (!app) return;
  Array.from(app.children).forEach((node) => {
    if (node.matches('#authBackdrop, .authModal, #actionToast')) return;
    if (active) {
      if (!node.dataset.modalAriaHidden) {
        node.dataset.modalAriaHidden = node.getAttribute('aria-hidden') ?? '__none__';
      }
      node.inert = true;
      node.setAttribute('aria-hidden', 'true');
    } else if (node.dataset.modalAriaHidden) {
      node.inert = false;
      const previous = node.dataset.modalAriaHidden;
      if (previous === '__none__') node.removeAttribute('aria-hidden');
      else node.setAttribute('aria-hidden', previous);
      delete node.dataset.modalAriaHidden;
    }
  });
  document.body.classList.toggle('modalOpen', active);
}

function modalFocusableElements(modal) {
  if (!(modal instanceof Element)) return [];
  return Array.from(modal.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'))
    .filter((element) => element.getClientRects().length > 0);
}

function closeAuthModals({ restoreFocus = true } = {}) {
  const opener = state.modal.opener;
  $('authBackdrop').hidden = true;
  $('loginModal').hidden = true;
  $('registerModal').hidden = true;
  setModalBackgroundInert(false);
  state.modal.kind = '';
  state.modal.opener = null;
  if (restoreFocus && opener instanceof HTMLElement && opener.isConnected) {
    window.setTimeout(() => opener.focus(), 0);
  }
}

function openAuthModal(kind, opener = document.activeElement) {
  if (state.session) {
    closeAuthModals();
    return;
  }
  setConnectionPanel(false);
  closeNavigationMenu();
  state.modal.kind = kind;
  state.modal.opener = opener instanceof HTMLElement ? opener : null;
  $('authBackdrop').hidden = false;
  $('loginModal').hidden = kind !== 'login';
  $('registerModal').hidden = kind !== 'register';
  setModalBackgroundInert(true);
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
  const adminGroup = document.querySelector('[data-nav-group="admin"]');
  if (adminGroup) adminGroup.hidden = !admin;
}

function huntEngineCapabilities(session = state.session) {
  return session?.huntEngineCapabilities || {};
}

function hasHuntEngineCapability(capability, session = state.session) {
  return Boolean(huntEngineCapabilities(session)[capability]);
}

function setHuntEngineVisibility(access) {
  $$('.huntEngineOnly').forEach((element) => {
    if (element.classList.contains('pageView')) {
      if (!access) element.hidden = true;
      return;
    }
    element.hidden = !access;
  });
  const group = document.querySelector('[data-nav-group="hunt-engine"]');
  if (group) group.hidden = !access;
}

function setRoleNavigation(session) {
  const signedIn = Boolean(session);
  const playerGroup = document.querySelector('[data-nav-group="player"]');
  if (playerGroup) playerGroup.hidden = !signedIn;
  $$('[data-nav-role="player"]').forEach((element) => {
    element.hidden = !signedIn;
  });
}

function identityClaimRequired() {
  return state.session?.identityBound === false;
}

function setIdentityClaimNotice(id, required) {
  const notice = $(id);
  if (!notice) return;
  notice.hidden = !required;
  if (required) notice.textContent = t('identity.claimRequired');
}

function updateChatAccess() {
  const signedIn = Boolean(state.session);
  const claimRequired = identityClaimRequired();
  const form = $('homeChatForm');
  const input = $('homeChatInput');
  form.hidden = !signedIn;
  form.querySelectorAll('input, button').forEach((control) => {
    control.disabled = !signedIn || claimRequired;
  });
  input.placeholder = !signedIn
    ? t('chat.login')
    : claimRequired
      ? t('identity.claimRequired')
      : t('chat.placeholder');
  setIdentityClaimNotice('chatIdentityClaimNotice', claimRequired);
}

function updateCommandAccess() {
  const claimRequired = identityClaimRequired();
  [
    ...$$('#commandForm input, #commandForm button'),
    ...$$('#tools .quickRow [data-command]'),
    ...$$('#tools .commandAction input, #tools .commandAction select, #tools .commandAction textarea, #tools .commandAction button')
  ].forEach((control) => {
    control.disabled = claimRequired;
  });
  setIdentityClaimNotice('commandIdentityClaimNotice', claimRequired);
}

function updateSessionChrome() {
  const session = state.session;
  const admin = Boolean(session?.admin);
  setAdminVisibility(admin);
  setHuntEngineVisibility(hasHuntEngineCapability('read', session));
  setRoleNavigation(session);
  if (!admin && ADMIN_PAGES.includes(state.page)) showPage('overview');
  if (!hasHuntEngineCapability('read', session) && HUNT_ENGINE_PAGES.includes(state.page)) showPage('overview');
  if (!session && state.page === 'tools') showPage('overview');
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
  updateChatAccess();
  updateCommandAccess();
}

function renderHealth(health) {
  const safe = health || { status: 'disabled', alerts: [] };
  $('healthStatus').textContent = statusLabel(safe.status || 'ok');
  $('healthStatus').className = `statusPill ${severityClass(safe.status)}`;
  $('healthList').innerHTML = safe.alerts?.length
    ? safe.alerts.map((alert) => dataItem(alert.label, alert.detail, statusLabel(alert.severity))).join('')
    : dataItem(t('health.label'), t('health.heap', { value: Number(safe.memoryUsagePercent || 0).toFixed(1) }), t('health.noAlerts'));
}

function chatSourceLabel(source) {
  switch (String(source || 'game').toLowerCase()) {
    case 'web': return 'WEB';
    case 'ai': return 'AI';
    case 'ai-npc': return 'NPC AI';
    case 'ai-fake-player': return 'BOT AI';
    case 'system': return 'SYS';
    case 'game':
    default: return 'GAME';
  }
}

function renderHomeChat(lines = state.chatLines) {
  state.chatLines = Array.isArray(lines) ? lines : [];
  $('chatStatus').textContent = String(state.chatLines.length);
  updateChatAccess();
  $('homeChatList').innerHTML = state.chatLines.length
    ? state.chatLines.slice(-80).map((line) => `
      <div class="chatLine" data-source="${esc(line.source || 'game')}">
        <strong><small>${esc(chatSourceLabel(line.source))}</small>${esc(line.sender || 'server')}</strong>
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
  const playerMeta = data.players ? `${data.players.length} visible` : t('players.loginRequired');
  $('overviewHero').innerHTML = [
    summaryCard('TPS', Number(data.server?.tps1 || 0).toFixed(2), `${Number(data.server?.mspt || 0).toFixed(1)} MSPT`, Number(data.server?.tps1 || 0) >= 18 ? 'good' : Number(data.server?.tps1 || 0) >= 15 ? 'warn' : 'bad'),
    summaryCard(t('metric.online'), `${data.server?.online || 0}/${data.server?.maxPlayers || 0}`, playerMeta),
    summaryCard(t('metric.memory'), data.server?.memory || '--', data.health?.status ? statusLabel(data.health.status) : '--', data.health?.status === 'critical' ? 'bad' : data.health?.status === 'warning' ? 'warn' : 'good'),
    summaryCard(t('optimization.mode'), data.optimization?.mode || '--', `${data.optimization?.coreWorkers || '--'} core workers`)
  ].join('');

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

  $('runtimeHero').innerHTML = [
    summaryCard(t('runtime.throttle'), data.optimization?.aiThrottleFactor || '--', `${data.optimization?.fakePlayerRuntimeIntervalSeconds || '--'}s`),
    summaryCard(t('runtime.queueThreads'), (data.queues || []).filter((queue) => queue.active).length || 0, `${(data.queues || []).length} queues`),
    summaryCard(t('queues.title'), (data.queues || []).reduce((total, queue) => total + Number(queue.queued || 0), 0), 'queued jobs'),
    summaryCard(t('hotpaths.title'), (data.hotPaths || []).length || 0, 'current samples')
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

  const pluginSearch = $('pluginSearch');
  const pluginFilter = $('pluginFilter');
  const pluginQuery = pluginSearch ? pluginSearch.value.trim().toLowerCase() : '';
  const pluginFilterValue = pluginFilter ? pluginFilter.value : 'all';
  const filteredPlugins = data.plugins
    ? data.plugins.filter((plugin) => pluginMatches(plugin, pluginQuery, pluginFilterValue))
    : null;
  const pluginPageCount = filteredPlugins ? Math.max(1, Math.ceil(filteredPlugins.length / PLUGIN_PAGE_SIZE)) : 0;
  if (filteredPlugins) {
    state.pluginPage = Math.max(0, Math.min(state.pluginPage, pluginPageCount - 1));
  }
  const pluginPageItems = filteredPlugins
    ? filteredPlugins.slice(state.pluginPage * PLUGIN_PAGE_SIZE, state.pluginPage * PLUGIN_PAGE_SIZE + PLUGIN_PAGE_SIZE)
    : null;
  if ($('pluginSummary')) {
    $('pluginSummary').textContent = data.plugins ? pluginSummaryLine(data.plugins) : '';
  }
  if ($('pluginReleaseReadiness')) {
    $('pluginReleaseReadiness').innerHTML = data.plugins ? pluginReadinessPanel(data.plugins) : '';
    $('pluginReleaseReadiness').classList.toggle('mutedState', !data.plugins);
  }
  if ($('pluginWorkbenchCards')) {
    $('pluginWorkbenchCards').innerHTML = data.plugins ? pluginWorkbenchCards(data.plugins, data.webSettings?.thirdParty || {}) : '';
  }
  $('pluginList').innerHTML = filteredPlugins
    ? pluginPageItems.map((plugin) => pluginLine(plugin, Boolean(data.session?.admin))).join('') || `<p class="mutedState">${esc(t('plugins.empty'))}</p>`
    : `<p class="mutedState">${esc(t('plugins.loginRequired'))}</p>`;
  $('pluginList').classList.toggle('mutedState', !data.plugins);
  renderPluginPager(filteredPlugins ? filteredPlugins.length : 0, state.pluginPage, pluginPageCount, Boolean(filteredPlugins));
  if ($('pluginCountBadge')) {
    $('pluginCountBadge').textContent = filteredPlugins ? t('plugins.count', { count: filteredPlugins.length }) : '--';
  }
  if (data.plugins) state.plugins = data.plugins;
  renderPluginInspector(filteredPlugins || []);
}

function renderPluginPager(total, page, pageCount, visible) {
  const pager = $('pluginPager');
  if (!pager) return;
  if (!visible || total <= PLUGIN_PAGE_SIZE || pageCount <= 1) {
    pager.hidden = true;
    pager.innerHTML = '';
    return;
  }
  const prevPage = Math.max(0, page - 1);
  const nextPage = Math.min(pageCount - 1, page + 1);
  pager.hidden = false;
  pager.innerHTML = `
    <button type="button" class="smallButton" data-plugin-page="${prevPage}" ${page <= 0 ? 'disabled' : ''}>${esc(t('plugins.prevPage'))}</button>
    <span>${esc(t('plugins.page', { page: page + 1, pages: pageCount, count: total }))}</span>
    <button type="button" class="smallButton" data-plugin-page="${nextPage}" ${page >= pageCount - 1 ? 'disabled' : ''}>${esc(t('plugins.nextPage'))}</button>
  `;
}

function huntEngineStatusTone(status) {
  if (!status?.available) return 'bad';
  const lifecycle = String(status.lifecycle || '').toLowerCase();
  return ['ready', 'running', 'published'].includes(lifecycle) ? 'good' : 'warn';
}

function safeHuntEngineUrl(value) {
  const raw = String(value || '').trim();
  if (!raw) return '';
  try {
    const url = new URL(raw, window.location.origin);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
  } catch {
    return '';
  }
}

function huntEngineOperationTime(value) {
  const time = Number(value);
  return Number.isFinite(time) && time > 0 ? new Date(time).toLocaleTimeString() : '--';
}

function rememberHuntEngineOperation(operation) {
  if (!operation?.id) return;
  state.huntEngineOperations = [operation, ...state.huntEngineOperations.filter((entry) => entry?.id !== operation.id)].slice(0, 8);
}

function renderHuntEngineOperations() {
  const target = $('huntEngineOperationList');
  if (!target) return;
  const rows = state.huntEngineOperations.map((operation) => dataItem(
    `${String(operation.type || 'operation').toUpperCase()} · ${String(operation.state || 'queued').toUpperCase()}`,
    operation.message || '--',
    `rev ${operation.contentRevision ?? '--'} · ${huntEngineOperationTime(operation.startedAt)}`
  ));
  target.innerHTML = rows.join('') || `<p class="mutedState">${esc(t('huntEngine.noOperations'))}</p>`;
}

function renderHuntEngineMigration(migration, includeEntries = false) {
  const target = $('huntEngineMigrationList');
  if (!target) return;
  const summary = migration || {};
  const rows = [
    dataItem('State', summary.state || '--', summary.message || ''),
    dataItem('Entries', summary.entryCount ?? 0, summary.journalLocation || '')
  ];
  if (includeEntries && Array.isArray(summary.entries)) {
    rows.push(...summary.entries.map((entry) => dataItem(
      entry.source || '--',
      entry.state || '--',
      [entry.target, entry.message].filter(Boolean).join(' · ')
    )));
  }
  target.innerHTML = rows.join('') || `<p class="mutedState">${esc(t('huntEngine.noMigration'))}</p>`;
}

function applyHuntEngineCapabilityControls(available) {
  $$('[data-hunt-engine-capability]').forEach((control) => {
    const allowed = hasHuntEngineCapability(control.dataset.huntEngineCapability);
    control.disabled = !allowed || !available;
    control.setAttribute('aria-disabled', String(!allowed || !available));
  });
}

function renderHuntEngine(huntEngine) {
  if (!hasHuntEngineCapability('read') || !huntEngine) return;
  if (anyFormDirty(['huntEngineSimpleItemForm', 'huntEngineUploadForm', 'huntEngineSendPackForm'])) return;
  const status = huntEngine.status || {};
  const catalogue = huntEngine.catalogue || {};
  const contents = catalogue.contents || [];
  const packages = huntEngine.packages || [];
  const resourcePack = huntEngine.resourcePack || {};
  const migration = huntEngine.migration || {};
  const packUrl = safeHuntEngineUrl(resourcePack.url);
  const available = Boolean(status.available);
  // Some HuntEngine hosts intentionally keep the delivery URL private or mint a
  // one-time URL per player. Publication state is authoritative even when there
  // is no safe static download link for the panel to expose.
  const published = Boolean(resourcePack.published);
  const configured = Boolean(resourcePack.configured);
  const packTitle = published
    ? t('huntEngine.packPublished')
    : configured ? t('huntEngine.packConfigured') : t('huntEngine.packMissing');
  $('huntEngineHero').innerHTML = [
    summaryCard(t('huntEngine.contents'), contents.length, `rev ${catalogue.revision ?? status.contentRevision ?? '--'}`, available ? 'good' : 'bad'),
    summaryCard(t('huntEngine.staged'), packages.length, status.lifecycle || '--', huntEngineStatusTone(status)),
    summaryCard(t('huntEngine.resourcePack'), published ? t('huntEngine.packPublished') : configured ? t('huntEngine.packConfigured') : '--', resourcePack.revision || '--', published ? 'good' : configured ? 'warn' : 'bad'),
    summaryCard(t('huntEngine.migrationSummary'), migration.state || '--', `${migration.entryCount ?? 0} entries`, migration.state === 'failed' ? 'bad' : 'neutral')
  ].join('');
  $('huntEnginePackStatus').innerHTML = `
    <article class="resourcePackStatus ${published ? 'good' : configured ? 'warn' : 'bad'}">
      <div>
        <strong>${esc(packTitle)}</strong>
        <span>${esc(resourcePack.message || status.message || (available ? t('huntEngine.available') : t('huntEngine.unavailable')))}</span>
        <small>${esc(packUrl || '--')}</small>
      </div>
      <div class="contentPackageActions">
        ${packUrl ? `<a class="smallButton" href="${esc(packUrl)}" target="_blank" rel="noreferrer">Download</a>` : ''}
      </div>
    </article>`;
  $('huntEngineCatalogueList').innerHTML = contents.map((content) => {
    const meta = [content.kind, ...(content.categories || []), content.permission || 'no permission'].filter(Boolean).join(' · ');
    return `<article class="pluginItem">
      <div class="pluginTop"><span>${esc(content.displayName || content.id)}<small>${esc(content.id)} · ${esc(meta)}</small></span></div>
      <div class="pluginMeta">${esc(content.description || '--')} · ${content.enabled === false ? 'disabled' : 'enabled'}</div>
    </article>`;
  }).join('') || `<p class="mutedState">${esc(t('huntEngine.noContents'))}</p>`;
  $('huntEnginePackageList').innerHTML = packages.map((contentPackage) => `
    <article class="dataItem contentPackageCard">
      <span>${esc(contentPackage.fileName || contentPackage.id)}<small>${esc(contentPackage.id || '--')} · ${esc(formatBytes(contentPackage.size || 0))} · ${esc(contentPackage.state || '--')}</small></span>
      <span class="contentPackageActions">
        ${hasHuntEngineCapability('stage') ? `<button type="button" class="smallButton" data-hunt-engine-package-remove="${esc(contentPackage.id)}" ${available ? '' : 'disabled'}>${esc(t('huntEngine.removePackage'))}</button>` : ''}
      </span>
    </article>`).join('') || `<p class="mutedState">${esc(t('huntEngine.noPackages'))}</p>`;
  renderHuntEngineOperations();
  renderHuntEngineMigration(migration);
  applyHuntEngineCapabilityControls(available);
}

function updateHuntEngineSummary(huntEngine) {
  if (!huntEngine) return;
  state.lastData = { ...(state.lastData || {}), huntEngine };
  renderHuntEngine(huntEngine);
}

async function pollHuntEngineOperation(operationId, attempt = 0) {
  if (!operationId || attempt >= 20) return;
  await new Promise((resolve) => window.setTimeout(resolve, 900));
  try {
    const result = await json(`/api/admin/hunt-engine/operation/${encodeURIComponent(operationId)}`);
    if (!result.operation) return;
    rememberHuntEngineOperation(result.operation);
    renderHuntEngineOperations();
    const running = ['queued', 'running', 'pending'].includes(String(result.operation.state || '').toLowerCase());
    if (running) {
      await pollHuntEngineOperation(operationId, attempt + 1);
      return;
    }
    await refresh();
  } catch (error) {
    if (attempt === 0) setOutput(t('command.error', { message: error.message }));
  }
}

function renderTitles(titles) {
  if (!state.session?.admin || !titles) return;
  if (anyFormDirty(['titleForm', 'titleAssignForm'])) return;
  $('titlesModuleEnabled').checked = Boolean(titles.enabled);
  $('titlesHero').innerHTML = [
    summaryCard('Module', titles.enabled ? 'Enabled' : 'Disabled', `chat ${titles.displayChat} · tag ${titles.displayNametag} · tab ${titles.displayTab}`),
    summaryCard('Definitions', (titles.definitions || []).length, 'registered titles'),
    summaryCard('Players', (titles.players || []).length, 'online assignment view')
  ].join('');
  $('titleList').innerHTML = (titles.definitions || []).map((title) => `
    <article class="pluginItem">
      <div class="pluginTop"><span>${esc(title.displayName || title.id)}<small>${esc(title.id)} · ${esc(title.prefix || '')}</small></span></div>
      <div class="pluginMeta">${esc(title.description || '--')} · priority ${esc(title.priority)} · ${title.enabled ? 'enabled' : 'disabled'}</div>
      <div class="pluginActions">
        <button type="button" data-title-edit="${esc(title.id)}">Edit</button>
        <button type="button" data-title-remove="${esc(title.id)}">Remove</button>
      </div>
    </article>
  `).join('') || `<p class="mutedState">No titles yet.</p>`;
  $('titlePlayerList').innerHTML = (titles.players || []).map((player) => dataItem(
    player.name,
    player.active || 'none',
    `${(player.owned || []).join(', ') || 'none'} · visible ${player.visible}`
  )).join('') || `<p class="mutedState">No online players.</p>`;
}

function renderActorWorlds(worlds) {
  if (formIsDirty('actorForm')) return;
  const selected = $('actorWorld').value;
  const names = (worlds || []).map((world) => world.name);
  $('actorWorld').innerHTML = `<option value="">${esc(t('actors.spawnPoint'))}</option>` + names.map((name) => `<option value="${esc(name)}">${esc(name)}</option>`).join('');
  if (names.includes(selected)) $('actorWorld').value = selected;
}

function renderPluginInspector(plugins) {
  const inspector = $('pluginInspector');
  if (!inspector) return;
  const list = plugins || [];
  if (!list.length) {
    inspector.classList.add('mutedState');
    inspector.innerHTML = '<p>Select a plugin to inspect.</p>';
    state.selectedPlugin = '';
    return;
  }
  const selected = list.find((plugin) => plugin.name === state.selectedPlugin) || list[0];
  state.selectedPlugin = selected.name;
  inspector.classList.remove('mutedState');
  inspector.innerHTML = `
    <h3>${esc(selected.name)}</h3>
    <p class="subtleLine">${esc(selected.description || pluginRiskLabel(selected))}</p>
    <div class="compactList">
      ${dataItem('Status', pluginStatusLabel(selected.status || (selected.loaded !== false ? (selected.enabled ? 'enabled' : 'disabled') : 'installed')))}
      ${dataItem('Version', selected.version || '--', selected.apiVersion ? `API ${selected.apiVersion}` : '')}
      ${dataItem('Source', selected.sourceJar || '--', selected.main || '--')}
      ${dataItem('Authors', selected.authors?.join(', ') || '--', selected.website || '--')}
      ${dataItem('Dependencies', selected.dependencies?.join(', ') || '--', selected.softDependencies?.length ? `soft: ${selected.softDependencies.join(', ')}` : '')}
    </div>`;
}

function pluginWorkbenchCards(plugins, thirdParty) {
  const find = (names) => plugins.find((plugin) => names.includes((plugin.name || '').toLowerCase()));
  const status = (names) => pluginStatusLabel(find(names)?.status || 'missing');
  const bundled = thirdParty?.bundled || {};
  const geyser = thirdParty?.geyser || {};
  const ncr = thirdParty?.noChatReports || {};
  const huntEngine = thirdParty?.huntEngine || {};
  return [
    summaryCard('Cross-Platform', [
      `Geyser ${status(['geyser-spigot', 'geyser'])}`,
      `Floodgate ${status(['floodgate'])}`,
      `ViaVersion ${status(['viaversion'])}`,
      `ViaBackwards ${status(['viabackwards'])}`,
      `ViaRewind ${status(['viarewind'])}`,
      `Via Legacy ${status(['viarewind-legacy-support'])}`
    ].join(' · '), geyser.configPresent ? `${geyser.bedrockAddress || '0.0.0.0'}:${geyser.bedrockPort || 19132} · ${geyser.javaAuthType || 'floodgate'}` : 'Geyser config pending'),
    summaryCard('Chat & Privacy', `Built-in NCR ${ncr.builtinEnabled ? 'enabled' : 'disabled'}`, 'HunterCore core protection'),
    summaryCard('Custom Content', `HuntEngine ${status(['huntengine', 'hunt-engine'])} · ImageFrame ${status(['imageframe'])}`, `${bundled.huntEngine ? 'HuntEngine on' : 'HuntEngine off'} · ${huntEngine.lifecycle || 'not loaded'}`)
  ].join('');
}

function renderActorInspector(actors) {
  const inspector = $('actorInspector');
  if (!inspector) return;
  const list = actors || [];
  if (!list.length) {
    inspector.classList.add('mutedState');
    inspector.innerHTML = '<p>Select an actor to inspect.</p>';
    state.selectedActor = '';
    return;
  }
  const selected = list.find((actor) => actor.id === state.selectedActor) || list[0];
  state.selectedActor = selected.id;
  const location = selected.world
    ? `${selected.world} ${Number(selected.x).toFixed(1)} ${Number(selected.y).toFixed(1)} ${Number(selected.z).toFixed(1)}`
    : t('actors.notConfigured');
  inspector.classList.remove('mutedState');
  inspector.innerHTML = `
    <h3>${esc(selected.displayName)}</h3>
    <p class="subtleLine">${esc(selected.module)} · ${esc(selected.kind || selected.pose || '--')}</p>
    <div class="compactList">
      ${dataItem('State', selected.live ? t('actors.live') : t('actors.configured'), selected.aiEnabled ? t('actors.aiEnabled') : '--')}
      ${dataItem('Location', location)}
      ${dataItem(t('actors.clickCommand'), selected.clickCommand || '--')}
      ${dataItem(t('actors.aiStatus'), selected.aiStatus || '--', selected.aiPersona || selected.pose || '--')}
    </div>`;
}

function renderWebUserInspector(users) {
  const inspector = $('webUserInspector');
  if (!inspector) return;
  const list = users || [];
  if (!list.length) {
    inspector.classList.add('mutedState');
    inspector.innerHTML = '<p>Select a web role to inspect.</p>';
    state.selectedWebUser = '';
    return;
  }
  const selected = list.find((user) => user.id === state.selectedWebUser) || list[0];
  state.selectedWebUser = selected.id;
  inspector.classList.remove('mutedState');
  inspector.innerHTML = `
    <h3>${esc(selected.displayName)}</h3>
    <p class="subtleLine">${esc(roleLabel(selected.role))}</p>
    <div class="compactList">
      ${dataItem('Commands', selected.commandExecution ? t('webUsers.commandsOn') : t('webUsers.commandsOff'))}
      ${dataItem('Identity', selected.identityBound ? t('webUsers.identityBound') : t('webUsers.identityMissing'))}
      ${dataItem('Allowed', allowedLine(selected), selected.allowedCommandsConfigured ? 'custom' : 'inherit')}
    </div>`;
}

function renderActors(actors) {
  if (!state.session?.admin) return;
  if (formIsDirty('actorForm')) return;
  $('actorList').classList.remove('mutedState');
  $('actorList').innerHTML = actors?.length ? actors.map(actorLine).join('') : `<p class="mutedState">${esc(t('actors.none'))}</p>`;
  renderActorInspector(actors);
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
  renderWebUserInspector(state.webUsers);
}

function renderWebSettings(settings) {
  if (!state.session?.admin || !settings) return;
  if (formIsDirty('webSettingsForm')) return;
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
  $('authResourcePackGui').checked = settings.authResourcePackGui !== false;
  $('authResourcePackPromptOnJoin').checked = settings.authResourcePackPromptOnJoin !== false;
  $('authMinimumPasswordLength').value = settings.authMinimumPasswordLength ?? '';
  $('authLoginTimeoutSeconds').value = settings.authLoginTimeoutSeconds ?? '';
  $('authMaxLoginAttempts').value = settings.authMaxLoginAttempts ?? '';
  $('authLockoutSeconds').value = settings.authLockoutSeconds ?? '';
  $('authRegistrationUrl').value = settings.authRegistrationUrl || '';
  $('webCorsEnabled').checked = Boolean(settings.corsEnabled);
  $('webCorsAllowOrigin').value = settings.corsAllowOrigin || '*';
  $('webApiKeyEnabled').checked = Boolean(settings.apiKeyEnabled);
  $('webApiKey').placeholder = settings.apiKeyConfigured ? 'API key configured' : t('remote.apiKey');
  $('webClearApiKey').checked = false;
  $('webAddressLine').textContent = settings.address || '';
  $('webThreadingLine').textContent = `${t('webSettings.cpuMode')}: ${settings.cpuMode || 'single-thread'} · ${settings.asyncEnabled ? 'async' : 'sync'} · workers ${settings.recommendedWorkers || '--'} · F3 ${settings.f3ServerName || ''}`;
  const thirdParty = settings.thirdParty || {};
  const bundled = thirdParty.bundled || {};
  const geyser = thirdParty.geyser || {};
  $('bundleGeyser').checked = Boolean(bundled.geyser);
  $('bundleFloodgate').checked = Boolean(bundled.floodgate);
  $('bundleHuntEngine').checked = Boolean(bundled.huntEngine);
  $('bundleImageFrame').checked = Boolean(bundled.imageFrame);
  $('bundleViaLegacy').checked = Boolean(bundled.viaLegacy);
  const huntEngine = thirdParty.huntEngine || {};
  if ($('huntEngineSettingsLine')) {
    $('huntEngineSettingsLine').textContent = `${huntEngine.available ? t('huntEngine.available') : t('huntEngine.unavailable')} · ${huntEngine.lifecycle || '--'} · rev ${huntEngine.contentRevision ?? '--'}`;
  }
  const ncr = thirdParty.noChatReports || {};
  $('noChatReportsEnabled').checked = Boolean(ncr.builtinEnabled);
  $('noChatReportsAddQueryData').checked = ncr.addQueryData !== false;
  $('noChatReportsConvertToGameMessage').checked = ncr.convertToGameMessage !== false;
  $('noChatReportsDemandOnClient').checked = Boolean(ncr.demandOnClient);
  $('noChatReportsDebugLog').checked = Boolean(ncr.debugLog);
  $('noChatReportsDisconnectMessage').value = ncr.disconnectMessage || '';
  $('geyserBedrockAddress').value = geyser.bedrockAddress || '0.0.0.0';
  $('geyserBedrockPort').value = geyser.bedrockPort ?? 19132;
  $('geyserJavaAuthType').value = geyser.javaAuthType || 'floodgate';
  $('geyserPrimaryMotd').value = geyser.primaryMotd || '';
  $('geyserSecondaryMotd').value = geyser.secondaryMotd || '';
  $('geyserPassthroughMotd').checked = Boolean(geyser.passthroughMotd);
  $('geyserPassthroughPlayerCounts').checked = Boolean(geyser.passthroughPlayerCounts);
  $('geyserServerName').value = geyser.serverName || '';
  if ($('noChatReportsLine')) {
    $('noChatReportsLine').textContent = t('bundles.ncrLine', {
      builtin: ncr.builtinEnabled ? 'on' : 'off'
    });
  }
  if ($('geyserStatusLine')) {
    $('geyserStatusLine').textContent = t('geyser.status', {
      config: geyser.configPresent ? 'ready' : 'will be created',
      address: geyser.bedrockAddress || '0.0.0.0',
      port: geyser.bedrockPort || 19132,
      auth: geyser.javaAuthType || 'floodgate'
    });
  }
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

function renderAiFakePersonaList() {
  const profiles = state.aiFakePersonas || [];
  $('aiFakePersonaList').innerHTML = profiles.length ? profiles.map((profile, index) => `
    <div class="aiConfigItem" data-ai-fake-persona-index="${index}">
      <label class="checkLine"><input type="checkbox" data-ai-fake-persona-field="enabled" ${profile.enabled === false ? '' : 'checked'}> <span>${esc(profile.displayName || t('ai.personaName'))}</span></label>
      <div class="formGrid">
        <input data-ai-fake-persona-field="displayName" value="${esc(profile.displayName || '')}" placeholder="${esc(t('ai.personaName'))}">
        <input data-ai-fake-persona-field="aliases" value="${esc((profile.aliases || []).join(', '))}" placeholder="${esc(t('ai.personaAliases'))}">
        <input data-ai-fake-persona-field="defaultGoal" value="${esc(profile.defaultGoal || '')}" placeholder="${esc(t('ai.personaGoal'))}">
        <button type="button" class="secondaryButton" data-ai-fake-persona-remove="${index}">${esc(t('ai.remove'))}</button>
      </div>
      <textarea data-ai-fake-persona-field="systemPrompt" rows="4" spellcheck="false" placeholder="${esc(t('ai.personaPrompt'))}">${esc(profile.systemPrompt || '')}</textarea>
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
  state.aiFakePersonas = Array.from(document.querySelectorAll('[data-ai-fake-persona-index]')).map((row, index) => ({
    id: state.aiFakePersonas[index]?.id || '',
    enabled: row.querySelector('[data-ai-fake-persona-field="enabled"]')?.checked ?? true,
    displayName: row.querySelector('[data-ai-fake-persona-field="displayName"]')?.value.trim() || '',
    aliases: splitAliases(row.querySelector('[data-ai-fake-persona-field="aliases"]')?.value),
    defaultGoal: row.querySelector('[data-ai-fake-persona-field="defaultGoal"]')?.value.trim() || '',
    systemPrompt: row.querySelector('[data-ai-fake-persona-field="systemPrompt"]')?.value.trim() || ''
  })).filter((profile) => profile.displayName && profile.systemPrompt);
  state.storyPhases = Array.from(document.querySelectorAll('[data-story-phase-index]')).map((row, index) => ({
    id: state.storyPhases[index]?.id || '',
    durationSeconds: Number(row.querySelector('[data-story-phase-field="durationSeconds"]')?.value || 60),
    systemPrompt: row.querySelector('[data-story-phase-field="systemPrompt"]')?.value.trim() || '',
    playerLines: String(row.querySelector('[data-story-phase-field="playerLines"]')?.value || '').split('\n').map((line) => line.trim()).filter(Boolean),
    allowedActions: splitAliases(row.querySelector('[data-story-phase-field="allowedActions"]')?.value),
    allowAiFree: row.querySelector('[data-story-phase-field="allowAiFree"]')?.checked ?? false
  })).filter((phase) => phase.id && phase.systemPrompt);
}

function renderStoryPhaseList() {
  const phases = state.storyPhases || [];
  $('aiStoryPhaseList').innerHTML = phases.length ? phases.map((phase, index) => `
    <div class="aiConfigItem" data-story-phase-index="${index}">
      <div class="listHeader">
        <h4>${esc(phase.id || `phase-${index + 1}`)}</h4>
      </div>
      <div class="formGrid">
        <input data-story-phase-field="durationSeconds" inputmode="numeric" value="${esc(String(phase.durationSeconds ?? 60))}" placeholder="Duration seconds">
        <input data-story-phase-field="allowedActions" value="${esc((phase.allowedActions || []).join(', '))}" placeholder="Allowed actions">
        <label class="checkLine"><input type="checkbox" data-story-phase-field="allowAiFree" ${phase.allowAiFree ? 'checked' : ''}> <span>Allow AI-Free</span></label>
      </div>
      <label class="textEditorLabel">
        <span>System prompt</span>
        <textarea data-story-phase-field="systemPrompt" rows="4" spellcheck="false">${esc(phase.systemPrompt || '')}</textarea>
      </label>
      <label class="textEditorLabel">
        <span>Auto player lines</span>
        <textarea data-story-phase-field="playerLines" rows="4" spellcheck="false">${esc((phase.playerLines || []).join('\n'))}</textarea>
      </label>
    </div>
  `).join('') : `<p class="mutedState">No story phases.</p>`;
}

function renderCommandMessages(messages) {
  if (!state.session?.admin || !messages) return;
  if (formIsDirty('commandMessagesForm')) return;
  $('commandMessageAbout').value = (messages.about || []).join('\n');
  $('commandMessagePlugins').value = (messages.plugins || []).join('\n');
  $('commandMessageVersion').value = (messages.version || []).join('\n');
  $('commandMessageRules').value = (messages.rules || []).join('\n');
  $('commandMessageDiscord').value = (messages.discord || []).join('\n');
  $('commandMessageWebsite').value = (messages.website || []).join('\n');
  $('commandMessageMotd').value = (messages.motd || []).join('\n');
  $('commandMessageInfo').value = (messages.info || []).join('\n');
  $('commandMessageServer').value = (messages.server || []).join('\n');
  $('commandMessageLinks').value = (messages.links || []).join('\n');
  $('commandMessageQq').value = (messages.qq || []).join('\n');
  $('commandMessageGroup').value = (messages.group || []).join('\n');
  $('commandMessageOpDenied').value = (messages.opDenied || []).join('\n');
}

function renderAiSettings(settings) {
  if (!state.session?.admin || !settings) return;
  if (formIsDirty('aiSettingsForm')) return;
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
  $('aiFakePlayersQuickResponseMode').value = settings.fakePlayersQuickResponseMode || 'off';
  $('aiFakePlayersAllowMovement').checked = Boolean(settings.fakePlayersAllowMovement);
  $('aiFakePlayersAllowBreaking').checked = Boolean(settings.fakePlayersAllowBreaking);
  $('aiFakePlayersAllowPlacing').checked = Boolean(settings.fakePlayersAllowPlacing);
  $('aiFakePlayersAllowInteraction').checked = Boolean(settings.fakePlayersAllowInteraction);
  $('aiFakePlayersChatControlEnabled').checked = Boolean(settings.fakePlayersChatControlEnabled);
  $('aiFakePlayersChatControlAmbientEnabled').checked = Boolean(settings.fakePlayersChatControlAmbientEnabled);
  $('aiFakePlayersChatControlPrefix').value = settings.fakePlayersChatControlPrefix || '';
  $('aiFakePlayersChatControlCooldownSeconds').value = settings.fakePlayersChatControlCooldownSeconds ?? '';
  $('aiFakePlayersChatControlRequirePermission').checked = Boolean(settings.fakePlayersChatControlRequirePermission);
  $('aiFakePlayersChatControlPermission').value = settings.fakePlayersChatControlPermission || '';
  $('aiChatSystemPrompt').value = settings.chatSystemPrompt || '';
  $('aiNpcSystemPrompt').value = settings.npcSystemPrompt || '';
  $('aiFakePlayersSystemPrompt').value = settings.fakePlayersSystemPrompt || '';
  state.aiChatProfiles = Array.isArray(settings.chatProfiles) ? settings.chatProfiles : [];
  state.aiBotAliases = Array.isArray(settings.fakeBotAliases) ? settings.fakeBotAliases : [];
  state.aiFakePersonas = Array.isArray(settings.fakePlayerPersonas) ? settings.fakePlayerPersonas : [];
  state.storyPhases = Array.isArray(settings.storyPhases) ? settings.storyPhases : [];
  renderAiProfileList();
  renderAiBotAliasList();
  renderAiFakePersonaList();
  renderStoryPhaseList();
  $('aiStoryModeEnabled').checked = Boolean(settings.storyModeEnabled);
  $('aiStoryMainPlayerName').value = settings.storyMainPlayerName || '';
  $('aiStoryAiName').value = settings.storyAiName || '';
  $('aiStoryAiSkin').value = settings.storyAiSkin || '';
  $('aiStoryDialogueMode').value = settings.storyDialogueMode || 'auto';
  $('aiStoryMeltdownKickCommand').value = settings.storyMeltdownKickCommand || '';
  $('aiStoryMeltdownDestroyGoal').value = settings.storyMeltdownDestroyGoal || '';
  $('aiKeyStatus').textContent = settings.apiKeyConfigured ? t('ai.keyConfigured') : t('ai.keyMissing');
}

function renderPermissionSettings(settings) {
  if (!state.session?.admin || !settings) return;
  if (formIsDirty('permissionSettingsForm')) return;
  $('permissionFakePlayersChatControlEnabled').checked = Boolean(settings.fakePlayersChatControlEnabled);
  $('permissionFakePlayersChatControlAmbientEnabled').checked = Boolean(settings.fakePlayersChatControlAmbientEnabled);
  $('permissionFakePlayersChatControlRequirePermission').checked = Boolean(settings.fakePlayersChatControlRequirePermission);
  $('permissionFakePlayersChatControlPermission').value = settings.fakePlayersChatControlPermission || '';
  $('permissionFakePlayersChatControlPrefix').value = settings.fakePlayersChatControlPrefix || '';
  $('permissionFakePlayersChatControlCooldownSeconds').value = settings.fakePlayersChatControlCooldownSeconds ?? '';
  const permissions = settings.guiPermissions || {};
  $('guiPermissionList').innerHTML = `
    <div class="permissionGridHead" data-i18n="permissions.guiPermissions">${esc(t('permissions.guiPermissions'))}</div>
    ${GUI_PERMISSION_FIELDS.map(([key, label]) => {
      const item = permissions[key] || {};
      const fallback = item.defaultPermission || '';
      return `<label class="fieldLabel permissionField">
        <span>${esc(guiPermissionLabel(key, label))}</span>
        <input data-gui-permission-key="${esc(key)}" form="permissionSettingsForm" value="${esc(item.permission || fallback)}" placeholder="${esc(fallback)}">
        <small>${esc(t('permissions.default', { permission: fallback }))}</small>
      </label>`;
    }).join('')}
  `;
}

function setMapState(tone, titleKey, detailKey) {
  const panel = $('mapState');
  if (!panel) return;
  const ready = tone === 'ready';
  panel.hidden = ready;
  if (ready) return;
  panel.dataset.tone = tone;
  $('mapStateTitle').textContent = t(titleKey);
  $('mapStateLine').textContent = t(detailKey);
  $('mapRetryButton').hidden = tone === 'loading';
}

function setRefreshInterval(millis) {
  const next = Math.max(1500, Math.min(15000, Number(millis) || 5000));
  if (state.refreshTimer && state.pollMillis === next) return;
  if (state.refreshTimer) clearInterval(state.refreshTimer);
  state.refreshTimer = setInterval(() => {
    refresh().catch(() => {});
  }, next);
  state.pollMillis = next;
}

async function refresh() {
  if (standaloneFrontend() && !state.backendUrl) {
    renderBackendConnection();
    setConnectionStatus('idle');
    $('serverLine').textContent = t('remote.required');
    $('mapFrame').removeAttribute('src');
    setMapState('empty', 'map.emptyTitle', 'map.emptyDescription');
    return;
  }
  if (!state.connection.lastSuccessAt) setConnectionStatus('connecting');
  try {
    const data = await json('/api/status');
    state.lastData = data;
    state.session = data.session;
    state.csrf = data.session?.csrf || state.csrf;
    state.sessionToken = data.session?.token || state.sessionToken;
    state.connection.lastSuccessAt = Date.now();
    if (state.backendUrl && state.sessionToken) storeValue(SESSION_TOKEN_KEY, state.sessionToken);
    $('serverNameTitle').textContent = data.server.name || 'HunterCore';
    $('topServerName').textContent = data.server.name || 'HunterCore';
    $('serverLine').textContent = `${data.server.software || 'Minecraft'} · ${data.server.version}`;
    $('tps').textContent = Number(data.server.tps1).toFixed(2);
    $('mspt').textContent = Number(data.server.mspt).toFixed(1);
    $('players').textContent = `${data.server.online}/${data.server.maxPlayers}`;
    $('memory').textContent = data.server.memory;
    renderAuthPublic(data.auth);
    updateSessionChrome();
    renderHealth(data.health);
    renderBackendConnection();
    setConnectionStatus('online', state.backendUrl || t('connection.local'));
    renderOverview(data);
    renderHuntEngine(data.huntEngine);
    renderTitles(data.titles);
    renderActorWorlds(data.worlds);
    renderActors(data.actorDetails);
    renderOperations(data.modules);
    renderWebUsers(data.webUsers);
    renderWebSettings(data.webSettings);
    renderCommandMessages(data.commandMessages);
    renderAiApprovals(data.aiApprovals);
    renderAiSettings(data.aiSettings);
    renderPermissionSettings(data.permissionSettings);
    ensureAccessibleLabels();
    refreshChat().catch(() => {});
    refreshMap().catch(() => {});
    setRefreshInterval(Number(data.optimization?.guestStatusCacheMillis || 5000) * 2);
  } catch (error) {
    const stale = Boolean(state.lastData);
    setConnectionStatus(stale ? 'stale' : 'offline', error.message || '');
    $('serverLine').textContent = error.message || t('connection.offline');
    throw error;
  }
}

async function refreshMap() {
  if (standaloneFrontend() && !state.backendUrl) {
    $('mapFrame').removeAttribute('src');
    setMapState('empty', 'map.emptyTitle', 'map.emptyDescription');
    return;
  }
  if (!state.mapUrl) setMapState('loading', 'map.loadingTitle', 'map.loadingDescription');
  try {
    const map = await json('/api/map');
    if (!map.ok || !map.url) {
      state.mapUrl = '';
      $('mapFrame').removeAttribute('src');
      setMapState('empty', 'map.emptyTitle', 'map.emptyDescription');
      return;
    }
    $('mapLink').href = map.url;
    if (map.url !== state.mapUrl) {
      state.mapUrl = map.url;
      $('mapFrame').src = map.url;
    }
    setMapState('ready');
  } catch {
    setMapState('error', 'map.errorTitle', 'map.errorDescription');
  }
}

async function runCommand(command) {
  if (identityClaimRequired()) {
    setOutput(t('identity.claimRequired'));
    return;
  }
  const payload = JSON.stringify({ command });
  const result = await json('/api/command', { method: 'POST', body: payload });
  setOutput(result.message || t('command.dispatched'), result.output || '');
  await refresh();
}

function commandFromTemplate(form) {
  let command = form.dataset.commandTemplate || '';
  const values = Object.fromEntries(new FormData(form).entries());
  Object.entries(values).forEach(([key, value]) => {
    command = command.replaceAll(`{${key}}`, String(value || '').trim());
  });
  return command
    .replace(/\{[^}]+}/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function editWebUser(id) {
  const user = state.webUsers.find((candidate) => candidate.id === id);
  if (!user) return;
  $('webUserName').value = user.displayName;
  $('webUserRole').value = user.role;
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

async function bindServerIcon() {
  const mark = $('productMark');
  const image = $('serverIcon');
  if (!mark || !(image instanceof HTMLImageElement)) return;
  if (!image.dataset.iconBound) {
    image.dataset.iconBound = 'true';
    image.addEventListener('load', () => {
      mark.classList.add('hasIcon');
      image.hidden = false;
    });
    image.addEventListener('error', () => {
      mark.classList.remove('hasIcon');
      image.hidden = true;
    });
  }
  mark.classList.remove('hasIcon');
  image.hidden = true;
  image.removeAttribute('src');
  const url = `${assetUrl('/assets/server-icon.png')}?${Date.now()}`;
  try {
    const response = await fetch(url, {
      credentials: state.backendUrl ? 'omit' : 'same-origin',
      cache: 'no-store'
    });
    if (!response.ok || !response.headers.get('content-type')?.startsWith('image/')) return;
    image.src = url;
  } catch {
    // A missing optional server icon should keep the HC fallback without a console network error.
  }
}

function closeNavigationMenu() {
  const nav = $('primaryNavigation');
  const toggle = $('moreToggle');
  nav?.classList.remove('isMenuOpen');
  toggle?.setAttribute('aria-expanded', 'false');
}

function toggleNavigationMenu() {
  const nav = $('primaryNavigation');
  const toggle = $('moreToggle');
  if (!nav || !toggle) return;
  const open = !nav.classList.contains('isMenuOpen');
  nav.classList.toggle('isMenuOpen', open);
  toggle.setAttribute('aria-expanded', String(open));
  if (open) setConnectionPanel(false);
}

function tracksDirtyState(form) {
  if (!(form instanceof HTMLFormElement) || form.dataset.transient === 'true') return false;
  if (form.matches('[data-command-template]')) return false;
  return !['loginForm', 'registerForm', 'connectionForm', 'homeChatForm', 'commandForm', 'aiTestForm', 'luckForm'].includes(form.id);
}

function bindFormStateTracking() {
  const mark = (event) => {
    const form = formForControl(event.target);
    if (tracksDirtyState(form)) markFormDirty(form, event.target);
  };
  document.addEventListener('input', mark, true);
  document.addEventListener('change', mark, true);
  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (!tracksDirtyState(form) && form.id !== 'connectionForm') return;
    state.activeForm = form;
    form.dataset.submitting = 'true';
    form.setAttribute('aria-busy', 'true');
    setFormState(form, t('form.saving'), 'saving', event.submitter);
  }, true);
}

function trapModalFocus(event) {
  if (!state.modal.kind) return false;
  const modal = state.modal.kind === 'register' ? $('registerModal') : $('loginModal');
  if (event.key === 'Escape') {
    event.preventDefault();
    closeAuthModals();
    return true;
  }
  if (event.key !== 'Tab') return false;
  const focusable = modalFocusableElements(modal);
  if (!focusable.length) {
    event.preventDefault();
    return true;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
  return true;
}

function bindEvents() {
  bindFormStateTracking();
  $$('[data-page-target]').forEach((button) => {
    button.addEventListener('click', () => {
      showPage(button.dataset.pageTarget);
    });
  });

  window.addEventListener('popstate', () => showPage(pageFromLocation(), false));

  $('languageToggle').addEventListener('click', () => {
    setLanguage(state.lang === 'zh' ? 'en' : 'zh');
  });

  $('sessionToggle').addEventListener('click', (event) => {
    openAuthModal('login', event.currentTarget);
  });

  $('registerToggle').addEventListener('click', (event) => openAuthModal('register', event.currentTarget));
  $('authBackdrop').addEventListener('click', closeAuthModals);
  $$('[data-close-auth]').forEach((button) => button.addEventListener('click', closeAuthModals));
  $('connectionToggle').addEventListener('click', () => {
    const panel = $('connectionPanel');
    setConnectionPanel(Boolean(panel?.hidden), true);
    closeNavigationMenu();
  });
  $$('[data-close-connection]').forEach((button) => button.addEventListener('click', () => setConnectionPanel(false)));
  $('moreToggle').addEventListener('click', toggleNavigationMenu);
  document.addEventListener('click', (event) => {
    const shell = document.querySelector('.topShell');
    if (shell && !shell.contains(event.target)) closeNavigationMenu();
  });
  window.addEventListener('resize', () => {
    if (window.innerWidth > 1180) closeNavigationMenu();
  });
  window.addEventListener('keydown', (event) => {
    if (trapModalFocus(event)) return;
    if (event.key !== 'Escape') return;
    if (!$('connectionPanel').hidden) {
      setConnectionPanel(false);
      return;
    }
    closeNavigationMenu();
  });
  $('mapRetryButton').addEventListener('click', () => refreshMap());
  $('mapFrame').addEventListener('error', () => setMapState('error', 'map.errorTitle', 'map.errorDescription'));

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
    if (identityClaimRequired()) {
      setOutput(t('identity.claimRequired'));
      return;
    }
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
    const nextBackendUrl = normalizeBackendUrl($('backendUrl').value);
    const backendChanged = nextBackendUrl !== state.backendUrl;
    state.backendUrl = nextBackendUrl;
    state.apiKey = $('backendApiKey').value.trim();
    state.csrf = '';
    if (backendChanged || !state.backendUrl) {
      state.sessionToken = '';
      state.session = null;
      state.lastData = null;
      state.mapUrl = '';
      $('mapFrame').removeAttribute('src');
      $('mapLink').href = '#';
      setMapState('loading', 'map.loadingTitle', 'map.loadingDescription');
      updateSessionChrome();
    }
    storeValue(BACKEND_URL_KEY, state.backendUrl);
    storeValue(BACKEND_API_KEY_KEY, state.apiKey);
    storeValue(SESSION_TOKEN_KEY, state.sessionToken);
    renderBackendConnection();
    if (!state.backendUrl && standaloneFrontend()) {
      setConnectionStatus('idle');
      state.activeForm = null;
      markFormClean($('connectionForm'), t('remote.required'), 'error', event.submitter);
      return;
    }
    setConnectionStatus('connecting');
    try {
      await refresh();
      await refreshMap();
      bindServerIcon();
      setOutput(t('remote.saved'));
      setConnectionPanel(false);
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
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
    try {
      await refresh();
      await refreshMap();
      setOutput(standaloneFrontend() ? t('remote.required') : t('remote.local'));
      if (!standaloneFrontend()) setConnectionPanel(false);
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
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

  $$('.commandAction[data-command-template]').forEach((form) => {
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const command = commandFromTemplate(form);
      if (!command) return;
      $('commandInput').value = command;
      try {
        await runCommand(command);
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
    const card = target instanceof Element ? target.closest('[data-actor-select]') : null;
    if (card?.dataset.actorSelect) {
      state.selectedActor = card.dataset.actorSelect;
      renderActorInspector(state.lastData?.actors || []);
      if (!(target instanceof HTMLButtonElement)) return;
    }
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
      authResourcePackGui: String($('authResourcePackGui').checked),
      authResourcePackPromptOnJoin: String($('authResourcePackPromptOnJoin').checked),
      authMinimumPasswordLength: $('authMinimumPasswordLength').value,
      authLoginTimeoutSeconds: $('authLoginTimeoutSeconds').value,
      authMaxLoginAttempts: $('authMaxLoginAttempts').value,
      authLockoutSeconds: $('authLockoutSeconds').value,
      authRegistrationUrl: $('authRegistrationUrl').value,
      corsEnabled: String($('webCorsEnabled').checked),
      corsAllowOrigin: $('webCorsAllowOrigin').value,
      apiKeyEnabled: String($('webApiKeyEnabled').checked),
      apiKey: $('webApiKey').value,
      clearApiKey: String($('webClearApiKey').checked),
      bundleGeyser: String($('bundleGeyser').checked),
      bundleFloodgate: String($('bundleFloodgate').checked),
      bundleHuntEngine: String($('bundleHuntEngine').checked),
      bundleImageFrame: String($('bundleImageFrame').checked),
      bundleViaLegacy: String($('bundleViaLegacy').checked),
      noChatReportsEnabled: String($('noChatReportsEnabled').checked),
      noChatReportsAddQueryData: String($('noChatReportsAddQueryData').checked),
      noChatReportsConvertToGameMessage: String($('noChatReportsConvertToGameMessage').checked),
      noChatReportsDemandOnClient: String($('noChatReportsDemandOnClient').checked),
      noChatReportsDebugLog: String($('noChatReportsDebugLog').checked),
      noChatReportsDisconnectMessage: $('noChatReportsDisconnectMessage').value,
      geyserBedrockAddress: $('geyserBedrockAddress').value,
      geyserBedrockPort: $('geyserBedrockPort').value,
      geyserJavaAuthType: $('geyserJavaAuthType').value,
      geyserPrimaryMotd: $('geyserPrimaryMotd').value,
      geyserSecondaryMotd: $('geyserSecondaryMotd').value,
      geyserPassthroughMotd: String($('geyserPassthroughMotd').checked),
      geyserPassthroughPlayerCounts: String($('geyserPassthroughPlayerCounts').checked),
      geyserServerName: $('geyserServerName').value
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
      version: $('commandMessageVersion').value,
      rules: $('commandMessageRules').value,
      discord: $('commandMessageDiscord').value,
      website: $('commandMessageWebsite').value,
      motd: $('commandMessageMotd').value,
      info: $('commandMessageInfo').value,
      server: $('commandMessageServer').value,
      links: $('commandMessageLinks').value,
      qq: $('commandMessageQq').value,
      group: $('commandMessageGroup').value,
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

  $('aiAddFakePersona').addEventListener('click', () => {
    syncAiConfigListsFromDom();
    state.aiFakePersonas.push({
      id: '',
      enabled: true,
      displayName: 'ActorAI',
      aliases: [],
      defaultGoal: '',
      systemPrompt: ''
    });
    renderAiFakePersonaList();
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

  $('aiFakePersonaList').addEventListener('click', (event) => {
    const button = event.target.closest('[data-ai-fake-persona-remove]');
    if (!button) return;
    syncAiConfigListsFromDom();
    state.aiFakePersonas.splice(Number(button.dataset.aiFakePersonaRemove), 1);
    renderAiFakePersonaList();
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
      fakePlayersQuickResponseMode: $('aiFakePlayersQuickResponseMode').value,
      fakePlayersAllowMovement: String($('aiFakePlayersAllowMovement').checked),
      fakePlayersAllowBreaking: String($('aiFakePlayersAllowBreaking').checked),
      fakePlayersAllowPlacing: String($('aiFakePlayersAllowPlacing').checked),
      fakePlayersAllowInteraction: String($('aiFakePlayersAllowInteraction').checked),
      fakePlayersChatControlEnabled: String($('aiFakePlayersChatControlEnabled').checked),
      fakePlayersChatControlAmbientEnabled: String($('aiFakePlayersChatControlAmbientEnabled').checked),
      fakePlayersChatControlPrefix: $('aiFakePlayersChatControlPrefix').value,
      fakePlayersChatControlCooldownSeconds: $('aiFakePlayersChatControlCooldownSeconds').value,
      fakePlayersChatControlRequirePermission: String($('aiFakePlayersChatControlRequirePermission').checked),
      fakePlayersChatControlPermission: $('aiFakePlayersChatControlPermission').value,
      fakeBotAliases: JSON.stringify(state.aiBotAliases),
      fakePlayerPersonas: JSON.stringify(state.aiFakePersonas),
      fakePlayersSystemPrompt: $('aiFakePlayersSystemPrompt').value,
      storyModeEnabled: String($('aiStoryModeEnabled').checked),
      storyMainPlayerName: $('aiStoryMainPlayerName').value,
      storyAiName: $('aiStoryAiName').value,
      storyAiSkin: $('aiStoryAiSkin').value,
      storyDialogueMode: $('aiStoryDialogueMode').value,
      storyMeltdownKickCommand: $('aiStoryMeltdownKickCommand').value,
      storyMeltdownDestroyGoal: $('aiStoryMeltdownDestroyGoal').value,
      storyPhases: JSON.stringify(state.storyPhases)
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

  $('permissionSettingsForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      fakePlayersChatControlEnabled: String($('permissionFakePlayersChatControlEnabled').checked),
      fakePlayersChatControlAmbientEnabled: String($('permissionFakePlayersChatControlAmbientEnabled').checked),
      fakePlayersChatControlRequirePermission: String($('permissionFakePlayersChatControlRequirePermission').checked),
      fakePlayersChatControlPermission: $('permissionFakePlayersChatControlPermission').value,
      fakePlayersChatControlPrefix: $('permissionFakePlayersChatControlPrefix').value,
      fakePlayersChatControlCooldownSeconds: $('permissionFakePlayersChatControlCooldownSeconds').value
    };
    $$('[data-gui-permission-key]').forEach((input) => {
      payload[`guiPermission.${input.dataset.guiPermissionKey}`] = input.value;
    });
    try {
      const result = await json('/api/admin/permission-settings', { method: 'POST', body: JSON.stringify(payload) });
      setOutput(t('permissions.saved'));
      if (result.settings) renderPermissionSettings(result.settings);
      if (result.aiSettings) renderAiSettings(result.aiSettings);
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
      commandExecution: String($('webUserCommandExecution').checked),
      allowedCommandsMode: $('webUserAllowedMode').value,
      allowedCommands: $('webUserAllowedCommands').value
    };
    try {
      await json('/api/admin/web-user/save', { method: 'POST', body: JSON.stringify(payload) });
      setOutput(t('webUsers.saved'));
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('webUserList').addEventListener('click', async (event) => {
    const target = event.target;
    const card = target instanceof Element ? target.closest('[data-user-select]') : null;
    if (card?.dataset.userSelect) {
      state.selectedWebUser = card.dataset.userSelect;
      renderWebUserInspector(state.webUsers);
      if (!(target instanceof HTMLButtonElement)) return;
    }
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

  $('huntEngineSimpleItemForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!hasHuntEngineCapability('stage')) return;
    const form = event.currentTarget;
    if (!(form instanceof HTMLFormElement) || !form.reportValidity()) return;
    try {
      const result = await json('/api/admin/hunt-engine/item/create', {
        method: 'POST',
        body: JSON.stringify({
          id: $('huntEngineSimpleItemId').value,
          material: $('huntEngineSimpleItemMaterial').value,
          displayName: $('huntEngineSimpleItemName').value,
          description: $('huntEngineSimpleItemDescription').value
        })
      });
      form.reset();
      updateHuntEngineSummary(result.huntEngine);
      setOutput(t('huntEngine.simpleItemStaged', { id: result.contentId || 'huntercraft:item' }), result.message || '');
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('huntEngineUploadForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!hasHuntEngineCapability('stage')) return;
    const file = $('huntEngineUploadFile').files?.[0];
    if (!file) {
      setOutput(t('huntEngine.choosePackage'));
      return;
    }
    if (file.size > 8 * 1024 * 1024) {
      setOutput(t('huntEngine.uploadTooLarge'));
      return;
    }
    try {
      const result = await json('/api/admin/hunt-engine/upload', {
        method: 'POST',
        body: JSON.stringify({ fileName: file.name, contentBase64: await fileToBase64(file) })
      });
      updateHuntEngineSummary(result.huntEngine);
      setOutput(result.message || t('huntEngine.packageStaged'));
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('huntEnginePackageList')?.addEventListener('click', async (event) => {
    const target = event.target instanceof Element ? event.target.closest('[data-hunt-engine-package-remove]') : null;
    if (!(target instanceof HTMLButtonElement) || !hasHuntEngineCapability('stage')) return;
    if (!window.confirm(t('huntEngine.confirmRemovePackage'))) return;
    try {
      const result = await json('/api/admin/hunt-engine/package/remove', {
        method: 'POST',
        body: JSON.stringify({ id: target.dataset.huntEnginePackageRemove || '' })
      });
      updateHuntEngineSummary(result.huntEngine);
      setOutput(result.message || t('huntEngine.packageRemoved'));
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $$('[data-hunt-engine-operation]').forEach((button) => {
    button.addEventListener('click', async () => {
      const operation = button.dataset.huntEngineOperation || '';
      const capability = operation === 'validate' ? 'read' : 'publish';
      if (!hasHuntEngineCapability(capability)) return;
      if (['build', 'publish', 'reload'].includes(operation)
        && !window.confirm(t('huntEngine.confirmOperation', { operation: button.textContent.trim() || operation }))) return;
      try {
        const result = await json(`/api/admin/hunt-engine/${encodeURIComponent(operation)}`, { method: 'POST', body: '{}' });
        rememberHuntEngineOperation(result.operation);
        renderHuntEngineOperations();
        setOutput(result.operation?.message || t('huntEngine.operationStarted'));
        pollHuntEngineOperation(result.operation?.id).catch(() => {});
      } catch (error) {
        setOutput(t('command.error', { message: error.message }));
      }
    });
  });

  $('huntEngineSendPackForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!hasHuntEngineCapability('publish')) return;
    try {
      const result = await json('/api/admin/hunt-engine/send-pack', {
        method: 'POST',
        body: JSON.stringify({ player: $('huntEngineSendPackPlayer').value })
      });
      setOutput(result.message || t('huntEngine.packSent'));
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('huntEngineMigrationButton')?.addEventListener('click', async () => {
    if (!hasHuntEngineCapability('admin')) return;
    try {
      const result = await json('/api/admin/hunt-engine/migration');
      renderHuntEngineMigration(result.migration, true);
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('titleForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const result = await json('/api/admin/title/save', {
        method: 'POST',
        body: JSON.stringify({
          id: $('titleId').value,
          displayName: $('titleDisplayName').value,
          prefix: $('titlePrefix').value,
          priority: $('titlePriority').value,
          permission: $('titlePermission').value,
          description: $('titleDescription').value,
          enabled: String($('titleEnabled').checked)
        })
      });
      if (result.titles) renderTitles(result.titles);
      setOutput('Title saved.');
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('titlesModuleEnabled')?.addEventListener('change', async (event) => {
    try {
      const result = await json('/api/admin/title/module', { method: 'POST', body: JSON.stringify({ enabled: String(event.target.checked) }) });
      if (result.titles) renderTitles(result.titles);
      setOutput('Titles module updated.');
      await refresh();
    } catch (error) {
      event.target.checked = !event.target.checked;
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('titleList')?.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-title-edit], [data-title-remove]');
    if (!button) return;
    const title = (state.lastData?.titles?.definitions || []).find((entry) => entry.id === (button.dataset.titleEdit || button.dataset.titleRemove));
    if (button.dataset.titleEdit && title) {
      $('titleId').value = title.id || '';
      $('titleDisplayName').value = title.displayName || '';
      $('titlePrefix').value = title.prefix || '';
      $('titlePriority').value = title.priority || 0;
      $('titlePermission').value = title.permission || '';
      $('titleDescription').value = title.description || '';
      $('titleEnabled').checked = title.enabled !== false;
      return;
    }
    if (button.dataset.titleRemove) {
      try {
        const result = await json('/api/admin/title/remove', { method: 'POST', body: JSON.stringify({ id: button.dataset.titleRemove }) });
        if (result.titles) renderTitles(result.titles);
        setOutput('Title removed.');
        await refresh();
      } catch (error) {
        setOutput(t('command.error', { message: error.message }));
      }
    }
  });

  $('titleAssignForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const result = await json('/api/admin/title/assign', {
        method: 'POST',
        body: JSON.stringify({
          player: $('titleAssignPlayer').value,
          titleId: $('titleAssignTitle').value,
          action: $('titleAssignAction').value
        })
      });
      if (result.titles) renderTitles(result.titles);
      setOutput('Title assignment updated.');
      await refresh();
    } catch (error) {
      setOutput(t('command.error', { message: error.message }));
    }
  });

  $('pluginSearch').addEventListener('input', () => {
    state.pluginPage = 0;
    if (state.lastData) renderOverview(state.lastData);
  });
  $('pluginFilter').addEventListener('change', () => {
    state.pluginPage = 0;
    if (state.lastData) renderOverview(state.lastData);
  });

  $('pluginPager')?.addEventListener('click', (event) => {
    const button = event.target instanceof Element ? event.target.closest('[data-plugin-page]') : null;
    if (!(button instanceof HTMLButtonElement) || button.disabled) return;
    const page = Number(button.dataset.pluginPage);
    if (!Number.isFinite(page)) return;
    state.pluginPage = Math.max(0, page);
    if (state.lastData) renderOverview(state.lastData);
  });

  $('pluginList').addEventListener('click', async (event) => {
    const target = event.target;
    const row = target instanceof Element ? target.closest('.pluginItem, [data-plugin-select]') : null;
    const pluginName = row?.querySelector?.('[data-plugin-name]')?.dataset?.pluginName || row?.dataset?.pluginSelect || '';
    if (pluginName) {
      state.selectedPlugin = pluginName;
      renderPluginInspector(state.plugins);
      if (!(target instanceof HTMLButtonElement)) return;
    }
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
if (standaloneFrontend() && !state.backendUrl) setConnectionPanel(true);
refresh()
  .then(() => {
    if (window.location.hash === '#register') openAuthModal('register');
    if (window.location.hash === '#login') openAuthModal('login');
  })
  .catch((error) => {
    $('serverLine').textContent = error.message;
  });
refreshMap().catch(() => {});
setRefreshInterval(standaloneFrontend() && !state.backendUrl ? 15000 : 5000);
