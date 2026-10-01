<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import aiHanfuAssistant from '@/assets/ai-hanfu-assistant.png'

const route = useRoute()
const router = useRouter()
const currentStep = ref(0)
const isTransitioning = ref(false)
const isTyping = ref(false)
const displayedText = ref('')
let typingTimer = null

// ============ 轮播内容：13 张图，每图解说 + 5 道选择题 ============
const slides = [
  {
    image: new URL('@/assets/popup/step1-study.jpg', import.meta.url).href,
    label: '书房',
    title: '青灯黄卷',
    narration: '欢迎来到薪火讲堂！我是你们的解说员。看这间古书房——油灯、卷轴、砚台，这是古人求知的天地。一盏青灯，一卷在手，便是与千年先贤的对话。五千年文明，就从这方寸书房开始流传……'
  },
  {
    image: new URL('@/assets/popup/step2-opera.jpg', import.meta.url).href,
    label: '戏曲',
    title: '粉墨登场',
    narration: '再看这戏台！脸谱五彩斑斓，红脸忠义、白脸奸诈、黑脸刚直。生旦净丑，各有风骨；唱念做打，皆是功夫。一方戏台，便是半个天下——这是中国人独有的戏剧美学。'
  },
  {
    image: new URL('@/assets/popup/step3-hanfu.jpg', import.meta.url).href,
    label: '汉服',
    title: '衣冠之礼',
    narration: '汉服之美，在于端庄典雅。襦裙、曲裾、直裰、道袍……一针一线皆藏礼制，一衣一冠尽是文化。华夏衣冠，礼仪之邦——穿上汉服，穿上的不只是衣服，而是五千年文明的传承。'
  },
  {
    image: new URL('@/assets/popup/step4-farming.jpg', import.meta.url).href,
    label: '农耕',
    title: '稼穑之道',
    narration: '民以食为天！看这水车、锄头、竹筐——中国人用智慧改造自然，让江南水乡成为鱼米之乡。春种夏耘，秋收冬藏，一粒米一杯水，皆来自千年不断的农耕文明。'
  },
  {
    image: new URL('@/assets/popup/step5-medicine.jpg', import.meta.url).href,
    label: '中医',
    title: '岐黄之术',
    narration: '神农尝百草，伏羲制九针。药碾、秤杆、药柜——一株株草叶，承载着古人对生命的敬畏。中医不只是医术，更是一种哲学：天人合一，阴阳平衡，这是中华民族的健康守护。'
  },
  {
    image: new URL('@/assets/popup/step6-tea.jpg', import.meta.url).href,
    label: '茶道',
    title: '茶禅一味',
    narration: '柴米油盐酱醋茶，琴棋书画诗酒花茶。煮一壶茶，点一炷香，看热气袅袅——茶与禅，一饮一悟，这是中国人最优雅的生活哲学。来，且品这半盏清欢。'
  },
  {
    image: new URL('@/assets/popup/step7-reading.jpg', import.meta.url).href,
    label: '读书',
    title: '寒窗苦读',
    narration: '万般皆下品，惟有读书高。竹简、卷轴、书案——古人席地而坐，一卷在手，便与千年先贤对话。学而时习之，不亦说乎？读书改变命运，教育传承文明，古今皆然。'
  },
  {
    image: new URL('@/assets/popup/step8-carriage.jpg', import.meta.url).href,
    label: '车马',
    title: '千里之行',
    narration: '古道西风瘦马，夕阳西下，断肠人在天涯。一辆车，一匹马，承载着多少游子的乡愁，运送着多少商贾的货物。丝绸之路上的驼铃声，就是从这样的车马声中传向远方的。'
  },
  {
    image: new URL('@/assets/popup/step9-lantern.jpg', import.meta.url).href,
    label: '灯火',
    title: '灯火阑珊',
    narration: '东风夜放花千树，更吹落，星如雨。红灯笼、煤油灯、蜡烛——在没有电灯的年代，灯火是家的温暖，是夜的希望。万家灯火，就是人间最朴素的幸福。'
  },
  {
    image: new URL('@/assets/popup/step10-brush.jpg', import.meta.url).href,
    label: '书法',
    title: '笔走龙蛇',
    narration: '笔架、墨台、宣纸——文房四宝，缺一不可。王羲之的行书飘若浮云，颜真卿的楷书体势浑厚。一笔一划皆是风骨，一墨一彩尽是精神。中国书法，是世界上独一无二的艺术。'
  },
  {
    image: new URL('@/assets/popup/step11-documents.jpg', import.meta.url).href,
    label: '文书',
    title: '文牍案卷',
    narration: '卷轴、印章、书信——纸短情长，见字如面。一封家书，一纸公文，承载着家国天下的嘱托。文字，是文明最忠实的记录者，也是穿越千年的信使。'
  },
  {
    image: new URL('@/assets/popup/step12-money.png', import.meta.url).href,
    label: '货币',
    title: '孔方天下',
    narration: '钱文方孔，古已有之。铜钱、银锭、算盘——一枚枚钱币，一桩桩买卖，编织出古代中国的商业网络。算盘一响，黄金万两；方圆之间，藏着天下的聚散。'
  },
  {
    image: new URL('@/assets/popup/step13-classroom.jpg', import.meta.url).href,
    label: '学堂',
    title: '蒙童启智',
    narration: '三人行，必有我师焉。学堂里，先生执鞭，学子伏案，朗朗书声中，一代又一代英才成长。腹有诗书气自华——读书改变命运，教育传承文明，这就是薪火相传的真义。'
  }
]

// ============ 每图 5 道选择题（结合该图主题） ============
const quizBank = {
  书房: [
    { q: '古人书房中，下列哪样不属于"文房四宝"？', options: ['笔墨', '纸砚', '毛笔筒与铜镜', '卷轴'], answer: 2, tip: '文房四宝指"笔、墨、纸、砚"，铜镜用于梳妆，与书写无关。' },
    { q: '"青灯黄卷"形容的是哪种生活状态？', options: ['灯下苦读、钻研学问', '通宵饮酒作乐', '深夜下棋博弈'], answer: 0, tip: '青灯即油灯，黄卷指古书卷轴，形容在灯下刻苦读书。' },
    { q: '古人把一卷书竹简或纸卷起来存放，主要为了什么？', options: ['装饰美观', '保护书籍、便于携带存放', '遮挡灰尘防虫蛀鼠咬'], answer: 1, tip: '卷轴形制轻便易收纳，是保护书籍的传统方式。' },
    { q: '书房悬"明镜高悬"或"清正廉明"类匾额，主要寓意是什么？', options: ['自勉修身、为官清白', '炫耀家宅富贵', '驱邪避灾保平安'], answer: 0, tip: '书房匾额多为修身自警之语，体现文人立身处世的准则。' },
    { q: '古书房里常备香炉焚香，最主要的作用之一是？', options: ['驱虫防霉、静心凝神助读', '熏制衣物防臭', '供奉祖先祈福'], answer: 0, tip: '焚香可驱赶蠹虫，香气也能让人心静，利于读书。' }
  ],
  戏曲: [
    { q: '戏曲脸谱中，红色脸谱通常代表什么人物？', options: ['忠义耿直，如关羽', '奸诈多疑，如曹操', '刚猛暴躁，如张飞'], answer: 0, tip: '红脸象征忠义，关羽是典型；白脸才是奸诈，黑脸多刚直。' },
    { q: '京剧"生旦净丑"中，"旦"指的是？', options: ['男性主角', '女性角色', '滑稽配角'], answer: 1, tip: '"旦"是戏曲中女性角色的统称，分青衣、花旦、刀马旦等。' },
    { q: '"唱念做打"是戏曲的四种基本功，其中"做"主要指？', options: ['舞蹈化的身段表演', '对唱对念', '武打翻腾'], answer: 0, tip: '"做"指身段表演，"打"指武打动作，"唱念"则是发声语言。' },
    { q: '京剧行当里，脸上勾画整张大花脸的角色叫？', options: ['净（花脸）', '丑', '末'], answer: 0, tip: '"净"即花脸，脸谱夸张，多演性格鲜明的权臣、武将。' },
    { q: '以下哪出戏与"粉墨登场"最贴切相关？', options: ['《贵妃醉酒》《霸王别姬》', '《雷雨》《茶馆》', '《阿Q正传》《边城》'], answer: 0, tip: '《贵妃醉酒》《霸王别姬》都是经典戏曲剧目，后者更是京剧代表作。' }
  ],
  汉服: [
    { q: '汉服"曲裾"的显著特征是？', options: ['衣襟呈三角螺旋缠绕', '上下分体裙裾相齐', '左右开襟系带交领'], answer: 0, tip: '曲裾是衣襟绕身缠绕式样，马王堆汉墓即出土过曲裾袍。' },
    { q: '"衣冠上国"说明古时衣冠与什么关系密切？', options: ['礼制等级与文明身份', '单纯的保暖需求', '商业交易符号'], answer: 0, tip: '古以衣冠辨尊卑、定礼仪，华夏亦被称为"衣冠之国"。' },
    { q: '古代"深衣"式汉服，其"被体深据"的穿着方式是？', options: ['衣襟绕至背后，衣长及足', '宽袍大袖拖地三尺', '短衣披帛束腰系带'], answer: 0, tip: '深衣上下连属，衣长掩身，是汉代士人常服。' },
    { q: '汉服中"襦裙"主要指哪类服饰？', options: ['上衣下裙的女式或童式装束', '男子朝服配冕旒', '冬季御寒棉袍'], answer: 0, tip: '襦为短衣，裙为下裳，襦裙是典型的上衣下裙搭配。' },
    { q: '"华夏衣冠，礼仪之邦"强调穿汉服的核心意义是？', options: ['传承礼制与文化认同', '追求时尚与炫耀', '区别于商贾阶层'], answer: 0, tip: '衣冠承载礼制，是文化传承与身份认同的载体。' }
  ],
  农耕: [
    { q: '下列哪样农具主要靠人力或畜力提水灌溉？', options: ['水车', '镰刀', '石磨'], answer: 0, tip: '水车可借水力或人力提水，是古代重要的灌溉工具。' },
    { q: '"春种夏耘，秋收冬藏"体现了哪种农耕智慧？', options: ['顺应节气、循环种植', '抢种抢收追求速度', '一年只种一季'], answer: 0, tip: '农耕讲究依二十四节气安排农事，循环有序。' },
    { q: '南方"江南水乡"成为鱼米之乡，主要得益于？', options: ['水稻种植与水网发达', '旱作小麦为主', '游牧放牧为业'], answer: 0, tip: '南方水热充足，河网密布，适宜稻作，故称鱼米之乡。' },
    { q: '"民以食为天"最早强调的是？', options: ['粮食是民生根本', '饮食文化讲究精致', '祭祀五谷为先'], answer: 0, tip: '此语出自《管子》，意为粮食乃百姓生存的根本。' },
    { q: '古代"二十四节气"中指导秋收的节气是？', options: ['秋分、霜降前后', '立春、雨水', '小满、芒种'], answer: 0, tip: '秋分后阳气渐收，霜降前后谷物成熟，宜收割储藏。' }
  ],
  中医: [
    { q: '"岐黄之术"中的"岐黄"指的是谁？', options: ['岐伯与黄帝', '扁鹊与华佗', '张仲景与孙思邈'], answer: 0, tip: '《黄帝内经》多以黄帝与臣岐伯问答形式写成，故以"岐黄"代指中医。' },
    { q: '中医"望闻问切"四诊中，"切"是指？', options: ['切脉诊脉', '切分草药', '切问病因'], answer: 0, tip: '"切"即脉诊，是中医诊断的重要方法。' },
    { q: '"天人合一"在中医里主要体现为？', options: ['人与自然的阴阳调和', '天上一星对人一穴', '用药须取自天上'], answer: 0, tip: '中医强调人随天地四时阴阳变化而调养，讲究天人相应。' },
    { q: '神农尝百草的传说反映了古人对什么的精神？', options: ['探索药性、敬畏生命', '追求长生不老', '炼丹修仙之术'], answer: 0, tip: '神农尝草辨药性的传说，体现古人探索医药的开拓精神。' },
    { q: '药柜上"秤杆"主要用途是？', options: ['精确称量药材分量', '丈量药材长度', '记录药方价格'], answer: 0, tip: '中药讲究配比剂量，须用秤杆称量，分毫不差。' }
  ],
  茶道: [
    { q: '"茶禅一味"表达的核心思想是？', options: ['品茶与修禅心境相通', '茶只配给禅客喝', '煮茶必须先打坐'], answer: 0, tip: '茶道讲究静心体悟，与禅宗注重心性相通，故谓"茶禅一味"。' },
    { q: '下列哪句与"柴米油盐酱醋茶"说法相合？', options: ['茶是百姓日常生活的必需', '茶只供帝王贵族', '茶为祭祀专用'], answer: 0, tip: '七件事是旧时百姓生计日常，茶位列其中，可见普及之广。' },
    { q: '古代文人"煮一壶茶，点一炷香"常伴哪种雅事？', options: ['抚琴读书品画', '对弈饮酒纵歌', '比武较力'], answer: 0, tip: '茶常与琴、书、画并称文人雅事，营造静心之境。' },
    { q: '"且品这半盏清欢"中"清欢"的含义是？', options: ['清淡而欢悦的闲适心境', '清冷的欢送宴', '清淡的菜肴款待'], answer: 0, tip: '"人间有味是清欢"指平淡中的真味与闲适之乐。' },
    { q: '中国茶文化中，招待来客的第一道常是？', options: ['奉上一杯热茶待客', '先让客自斟自饮', '以酒代茶敬客'], answer: 0, tip: '"以茶待客"是中国传统礼仪，一杯热茶是待客之道。' }
  ],
  读书: [
    { q: '"万般皆下品，惟有读书高"出自哪种背景？', options: ['科举时代崇尚读书做官', '唐代宫廷宴饮风俗', '宋代市井商业竞争'], answer: 0, tip: '此语出自《增广贤文》，反映科举时代读书可致仕的地位。' },
    { q: '"学而时习之，不亦说乎"是谁的话？', options: ['孔子', '老子', '孟子'], answer: 0, tip: '出自《论语·学而》，"说"通"悦"，强调学而实践之乐。' },
    { q: '古人"席地而坐"读书，主要因当时？', options: ['尚未普及高脚坐具', '地面凉快舒适', '席地更有仪式'], answer: 0, tip: '高桌高椅普及之前，古人多席地而坐，读书亦如此。' },
    { q: '"腹有诗书气自华"说的是？', options: ['读书多则气质自然高雅', '穿丝绸才显得富贵', '饱餐后方能文思泉涌'], answer: 0, tip: '出自苏轼，意为胸有诗书，气质自然不凡。' },
    { q: '"寒窗苦读"形容的是哪种求学态度？', options: ['在艰苦环境中坚持读书', '寒冬才能读书', '在窗前苦思冥想'], answer: 0, tip: '"寒窗"借指艰苦的读书环境，形容勤学不辍。' }
  ],
  车马: [
    { q: '"古道西风瘦马"化用了哪种出行意象？', options: ['马与旅途奔波', '牛拉犁田耕作', '船行江南水乡'], answer: 0, tip: '马是古代主要交通工具，瘦马古道勾勒旅途艰辛。' },
    { q: '丝绸之路主要靠什么动物驮运货物远行？', options: ['骆驼', '大象', '水牛'], answer: 0, tip: '骆驼耐渴耐饥，被称为"沙漠之舟"，是丝路主力。' },
    { q: '"千里之行，始于足下"原意强调？', options: ['远行须从第一步做起', '千里马一日千里', '车马越快越好'], answer: 0, tip: '出自《道德经》，意为再远的路也要从脚下迈出第一步。' },
    { q: '古代"车"的"轱辘"（车轮）主要作用？', options: ['减少摩擦、便于滚动', '增加车身高大', '装饰彰显身份'], answer: 0, tip: '车轮的发明让运输更高效，是人类交通史的大飞跃。' },
    { q: '"断肠人在天涯"中的"天涯"形容？', options: ['远离故乡的远方', '天边可见之处', '天涯海角景区'], answer: 0, tip: '"天涯"泛指极远之地，表达游子的思乡之情。' }
  ],
  灯火: [
    { q: '"东风夜放花千树"描写的场景是？', options: ['元宵夜满城灯火如树', '春天百花盛开', '夜晚放焰花庆典'], answer: 0, tip: '出自辛弃疾《青玉案·元夕》，形容元宵灯火之盛。' },
    { q: '在古代，"灯火"除了照明还有什么意义？', options: ['象征家的温暖与希望', '仅是取暖工具', '用来驱赶野兽'], answer: 0, tip: '万家灯火是团聚、平安的象征，是夜的希望。' },
    { q: '"灯火阑珊"形容的是？', options: ['灯火将残、人迹渐稀', '灯火通明热闹非凡', '灯火刚被点燃'], answer: 0, tip: '出自"众里寻他千百度，蓦然回首，那人却在灯火阑珊处"，指冷清尽头。' },
    { q: '在没有电灯的年代，古人夜间照明主要靠？', options: ['油灯、蜡烛', '火把、萤石', '荧光棒、电池'], answer: 0, tip: '油灯与蜡烛是古代主要的室内照明方式。' },
    { q: '"更吹落，星如雨"中的"星"暗指？', options: ['烟花散落的火星', '天上的星星坠落', '流星许愿'], answer: 0, tip: '辛弃疾借元宵烟花如星雨洒落，烘托热闹气氛。' }
  ],
  书法: [
    { q: '下列哪个是"文房四宝"的组成？', options: ['笔墨纸砚', '琴棋书画', '诗酒花茶'], answer: 0, tip: '文房四宝即笔、墨、纸、砚，书法作画必备。' },
    { q: '"王羲之的行书飘若浮云"说的是哪种字体？', options: ['行书', '隶书', '篆书'], answer: 0, tip: '王羲之被誉为"书圣"，行书代表作《兰亭集序》。' },
    { q: '颜真卿的楷书特点是？', options: ['体势浑厚庄重', '轻灵飘逸如云', '古拙像甲骨'], answer: 0, tip: '颜体楷书丰腴雄浑，结构端庄，是楷书典范。' },
    { q: '毛笔"尖、齐、圆、健"中，"尖"指？', options: ['笔锋聚拢尖锐', '笔杆短小', '墨汁浓稠'], answer: 0, tip: '毛笔讲究"四德"，尖指笔锋锐利聚拢。' },
    { q: '"笔走龙蛇"形容书法？', options: ['笔势矫健、线条流畅', '写字速度很快', '只用龙蛇图案装饰'], answer: 0, tip: '形容运笔如龙蛇游走，气韵生动。' }
  ],
  文书: [
    { q: '古代"见字如面"主要形容？', options: ['书信承载思念、纸短情长', '见面礼尚往来', '见长辈须下跪'], answer: 0, tip: '书信是古人传递情感的重要载体，见字如见其人。' },
    { q: '文书上的"印章"（官印）主要作用是？', options: ['证明文书的权威与真伪', '仅作装饰美观', '方便收藏展示'], answer: 0, tip: '印信是官方文书的凭证，防伪增信。' },
    { q: '"文牍案卷"中"文牍"指的是？', options: ['公文文书', '账簿钱文', '书信私物'], answer: 0, tip: '文牍指官府文书、公文，案卷则是归档文件。' },
    { q: '古代"家书抵万金"说明？', options: ['战乱中家人音讯弥足珍贵', '家书可换金白银', '写家书要花重金'], answer: 0, tip: '出自杜甫，表现战乱年代亲人消息的珍贵。' },
    { q: '文字在文明传承中主要扮演的角色是？', options: ['记录与传承的信使', '交易货币的替代', '军事指挥符号'], answer: 0, tip: '文字记录历史、传递知识，是文明延续的载体。' }
  ],
  货币: [
    { q: '古代铜钱"方孔"的设计主要便利？', options: ['穿绳串起、便于携带存放', '装饰美观', '象征天圆地方'], answer: 0, tip: '方孔便于用绳串钱，也暗合天圆地方之观念。' },
    { q: '"算盘一响，黄金万两"形容算盘在什么场合？', options: ['商业交易记账', '田间计量谷物', '宫廷乐舞演奏'], answer: 0, tip: '算盘是古代商业计算工具，买卖算账离不开它。' },
    { q: '银锭在历史上主要用作？', options: ['大额交易与储藏', '日常零钱找赎', '制作首饰为主'], answer: 0, tip: '银两用于大额支付与储藏，零用则折银或换铜钱。' },
    { q: '"方圆之间，藏着天下的聚散"暗喻货币？', options: ['流通往来影响财富聚散', '铸造工艺讲究方圆', '钱币形状决定买卖'], answer: 0, tip: '货币流通牵动经济，方与圆合璧，喻聚散之理。' },
    { q: '古代"孔方"是哪种物品的别称？', options: ['铜钱', '粮仓', '秤杆'], answer: 0, tip: '因铜钱方孔，故戏称"孔方兄"，代指钱财。' }
  ],
  学堂: [
    { q: '"三人行，必有我师焉"出自哪部经典？', options: ['《论语》', '《道德经》', '《诗经》'], answer: 0, tip: '孔子语，意为多人同行中总有可学之处，要虚心求教。' },
    { q: '古代私塾里"执鞭"的先生主要职责是？', options: ['管教训导学生读书', '负责学堂杂务', '管理学堂账目'], answer: 0, tip: '私塾先生执鞭训导，教授学生读书习字。' },
    { q: '"蒙童"一般指的是？', options: ['初入学堂的幼童', '蒙学出身的文人', '尚未启蒙的成人'], answer: 0, tip: '蒙童即启蒙阶段的孩子，是学堂最年幼的学生。' },
    { q: '朗朗书声体现的是哪种学习方式？', options: ['齐声诵读、强化记忆', '默读思考为主', '抄写默记为主'], answer: 0, tip: '古代学堂常以齐声诵读记忆经典，书声朗朗。' },
    { q: '"薪火相传，生生不息"的核心含义是？', options: ['知识与文明的代代传承', '香火祭祀不断', '木材须不断补充'], answer: 0, tip: '薪火相传比喻学问、技艺、文化代代接力延续。' }
  ]
}

const totalSteps = slides.length

// ============ 答题状态：每张图 5 道题，可继续加 ============
// quizState: { [slideLabel]: { answers: [optIndex|null,...], extra: [questions...] } }
const quizState = ref({})
function getQuiz(label) {
  if (!quizState.value[label]) {
    quizState.value[label] = { answers: Array(5).fill(null), extra: [] }
  }
  return quizState.value[label]
}
const currentQuiz = computed(() => {
  const slide = slides[currentStep.value]
  return getQuiz(slide.label)
})

// 当前该图"待答"的题号：5 道基础题里第一个还没选的（或额外题里第一个没选的）
function selectOption(qIndex, optIndex) {
  const st = getQuiz(slides[currentStep.value].label)
  if (qIndex < 0 || qIndex >= 5 || st.answers[qIndex] !== null) return
  st.answers[qIndex] = optIndex
}

function selectExtraAnswer(extraIndex, optIndex) {
  const st = getQuiz(slides[currentStep.value].label)
  const targetIndex = 5 + extraIndex
  if (targetIndex >= st.answers.length || st.answers[targetIndex] !== null) return
  st.answers[targetIndex] = optIndex
}

function correctCount() {
  const slide = slides[currentStep.value]
  const st = getQuiz(slide.label)
  let n = 0
  for (let i = 0; i < 5; i++) {
    if (st.answers[i] === quizBank[slide.label][i].answer) n++
  }
  return n
}
const answeredCount = computed(() => {
  return currentQuiz.value.answers.slice(0, 5).filter((a) => a !== null).length
})
const quizDone = computed(() => answeredCount.value >= 5)

// 实际成绩：答对题数 / 百分制 / 评语
const score = computed(() => {
  const label = currentSlide.value.label
  const bank = quizBank[label]
  const st = quizState.value[label]
  if (!st) return { total: 0, correct: 0, percent: 0, passed: false, verdict: '' }
  let correct = 0
  for (let i = 0; i < 5; i++) {
    if (st.answers[i] === bank[i].answer) correct++
  }
  const percent = Math.round((correct / 5) * 100)
  const passed = percent >= 60
  const verdict =
    percent === 100 ? '满分！博学多才'
      : percent >= 80 ? '优秀！理解深刻'
      : passed ? '良好！基础扎实'
      : '再读一遍解说，争取下次全对'
  return { total: 5, correct, percent, passed, verdict }
})

// 加号：追加一道该图主题的额外题（循环题库，最多再加 2 道）
function addExtraQuestion() {
  const slide = slides[currentStep.value]
  const st = getQuiz(slide.label)
  const bank = quizBank[slide.label]
  if (st.extra.length >= 2) return
  st.extra.push(bank[st.extra.length % bank.length])
}

// ============ 语音模块：优先用提供的音频素材，否则用浏览器语音合成(TTS)自动播报 ============
const audioUrls = {
  // 若提供了音频素材，填入即可覆盖 TTS。例：
  // '书房': new URL('@/assets/audio/study.mp3', import.meta.url).href
}
const isSpeaking = ref(false)
let currentAudio = null

function stopSpeaking() {
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }
  window.speechSynthesis?.cancel()
  isSpeaking.value = false
}

function pickChineseVoice() {
  const voices = window.speechSynthesis?.getVoices?.() || []
  return (
    voices.find((v) => /zh[-_]?CN/i.test(v.lang) && /male/i.test(v.name)) ||
    voices.find((v) => /zh[-_]?CN/i.test(v.lang)) ||
    voices.find((v) => /^zh/i.test(v.lang)) ||
    null
  )
}

// 若该图有音频素材则播素材，否则用浏览器 TTS 播报解说词
function speakNarration(text) {
  stopSpeaking()
  const slide = slides[currentStep.value]
  const src = audioUrls[slide.label]
  if (src) {
    const audio = new Audio(src)
    currentAudio = audio
    audio.onended = () => { currentAudio = null; isSpeaking.value = false }
    audio.onerror = () => { currentAudio = null; isSpeaking.value = false }
    audio.play().then(() => { isSpeaking.value = true }).catch(() => { currentAudio = null })
    return
  }
  if (!('speechSynthesis' in window)) {
    isSpeaking.value = false
    return
  }
  const utter = new SpeechSynthesisUtterance(text)
  utter.lang = 'zh-CN'
  utter.rate = 1
  utter.pitch = 1
  const voice = pickChineseVoice()
  if (voice) utter.voice = voice
  utter.onend = () => { isSpeaking.value = false }
  utter.onerror = () => { isSpeaking.value = false }
  window.speechSynthesis.speak(utter)
  isSpeaking.value = true
}

// 手动切换按钮：停止 / 再听一遍
function toggleSpeak() {
  if (isSpeaking.value) stopSpeaking()
  else speakNarration(slides[currentStep.value].narration)
}

// 是否使用了真实音频素材（有素材则按钮文案为"听一遍"，否则为"朗读"）
function hasAudio(label) {
  return Boolean(audioUrls[label])
}
const audioUsingFile = computed(() => hasAudio(currentSlide.value.label))

// ============ 轮播控制 ============
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/dashboard')
}

function nextStep() {
  if (isTransitioning.value) return
  if (currentStep.value < totalSteps - 1) {
    isTransitioning.value = true
    currentStep.value++
    setTimeout(() => { isTransitioning.value = false }, 500)
  }
}

function prevStep() {
  if (isTransitioning.value) return
  if (currentStep.value > 0) {
    isTransitioning.value = true
    currentStep.value--
    setTimeout(() => { isTransitioning.value = false }, 500)
  }
}

function goToStep(index) {
  if (isTransitioning.value) return
  isTransitioning.value = true
  currentStep.value = index
  setTimeout(() => { isTransitioning.value = false }, 500)
}

function handleKeydown(e) {
  if (document.activeElement?.tagName === 'INPUT') return
  if (e.key === 'ArrowRight' || e.key === ' ') nextStep()
  else if (e.key === 'ArrowLeft') prevStep()
  else if (e.key === 'Escape') goBack()
}

let touchStartX = 0
function handleTouchStart(e) { touchStartX = e.touches[0].clientX }
function handleTouchEnd(e) {
  const diff = e.changedTouches[0].clientX - touchStartX
  if (diff < -50) nextStep()
  else if (diff > 50) prevStep()
}

// ============ 打字机效果 + 自动播报 ============
function startTyping(text) {
  if (typingTimer) clearTimeout(typingTimer)
  isTyping.value = true
  displayedText.value = ''
  let i = 0
  const typeNext = () => {
    if (i < text.length) {
      displayedText.value += text[i]
      i++
      typingTimer = setTimeout(typeNext, 40)
    } else {
      isTyping.value = false
      // 解说打完，自动语音播报（有素材播素材，否则 TTS）
      speakNarration(text)
    }
  }
  typeNext()
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  startTyping(slides[0].narration)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (typingTimer) clearTimeout(typingTimer)
  stopSpeaking()
})

watch(currentStep, (newIdx) => {
  startTyping(slides[newIdx].narration)
})

// 答题面板是否展开（解说打字结束后出现）；用于让解说气泡上移，避免误触
const quizPanelOpen = computed(() => !isTyping.value && displayedText.value.length > 0)

const progressPercent = computed(() => ((currentStep.value + 1) / totalSteps) * 100)
const isFirst = computed(() => currentStep.value === 0)
const isLast = computed(() => currentStep.value === totalSteps - 1)
const currentSlide = computed(() => slides[currentStep.value])
const currentBank = computed(() => quizBank[currentSlide.value.label])


</script>

<template>
  <div
    class="carousel"
    :class="{ 'quiz-open': quizPanelOpen }"
    @touchstart="handleTouchStart"
    @touchend="handleTouchEnd"
  >

    <!-- 顶部导航 -->
    <div class="top-bar">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <div class="brand">
        <span class="brand-logo">薪</span>
        <span class="brand-name">薪火讲堂</span>
        <span class="brand-sub">· 科普</span>
      </div>
      <div class="page-num">{{ currentStep + 1 }} / {{ totalSteps }}</div>
    </div>

    <!-- 进度条 -->
    <div class="progress-track">
      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
    </div>

    <!-- 图片区域 -->
    <div class="image-area">
      <div
        v-for="(slide, index) in slides"
        :key="index"
        class="slide"
        :class="{ active: index === currentStep }"
      >
        <img :src="slide.image" :alt="slide.label" class="slide-img" />
        <div class="img-overlay"></div>
        <div class="img-title-area">
          <span class="slide-label">{{ slide.label }}</span>
          <h2 class="slide-title">{{ slide.title }}</h2>
          <button
            class="audio-btn"
            :class="{ playing: isSpeaking }"
            :title="isSpeaking ? '停止' : (audioUsingFile ? '听一遍' : '朗读')"
            @click="toggleSpeak"
          >
            <svg v-if="!isSpeaking" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 5L6 9H2v6h4l5 4V5zM15.54 8.46a5 5 0 010 7.07M19.07 4.93a10 10 0 010 14.14"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="6" y="6" width="12" height="12" rx="2"/>
            </svg>
            <span>{{ isSpeaking ? '停止' : (audioUsingFile ? '听一遍' : '朗读') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 左右箭头 -->
    <button v-if="!isFirst" class="arrow-btn arrow-left" @click="prevStep">
      <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M15 18l-6-6 6-6"/>
      </svg>
    </button>
    <button v-if="!isLast" class="arrow-btn arrow-right" @click="nextStep">
      <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M9 18l6-6-6-6"/>
      </svg>
    </button>

    <!-- 小人解说员 + 问答区 -->
    <div class="narrator">
      <div class="narrator-avatar">
        <img :src="aiHanfuAssistant" alt="解说员" />
        <div class="avatar-glow"></div>
      </div>
      <div class="speech-bubble">
        <div class="bubble-tail"></div>
        <p class="speech-text">
          {{ displayedText }}<span v-if="isTyping" class="cursor">|</span>
        </p>
      </div>
    </div>

    <!-- 答题区：独立可滚动面板，确保选项可点击 -->
    <div class="quiz-panel" v-if="!isTyping && displayedText">
      <div class="quiz-box">
          <div class="quiz-head">
            <span class="quiz-title">课后小问 · {{ currentSlide.label }}</span>
            <span v-if="!quizDone" class="quiz-progress">已答 {{ answeredCount }} / 5</span>
            <span v-else class="quiz-score" :class="{ pass: score.passed }">
              成绩 {{ score.correct }} / 5 · {{ score.percent }} 分
            </span>
          </div>

          <!-- 实际成绩横幅 -->
          <div v-if="quizDone" class="quiz-result" :class="{ pass: score.passed }">
            <span class="quiz-result-label">本次成绩</span>
            <span class="quiz-result-num">{{ score.percent }}<i>分</i></span>
            <span class="quiz-result-verdict">{{ score.verdict }}</span>
          </div>

          <div
            v-for="(q, qIndex) in currentBank"
            :key="qIndex"
            class="quiz-q"
            :class="{ answered: currentQuiz.answers[qIndex] !== null }"
          >
            <p class="quiz-question">{{ qIndex + 1 }}. {{ q.q }}</p>
            <div class="quiz-options">
              <button
                v-for="(opt, oIndex) in q.options"
                :key="oIndex"
                class="quiz-opt"
                :class="{
                  picked: currentQuiz.answers[qIndex] === oIndex,
                  correct: currentQuiz.answers[qIndex] !== null && oIndex === q.answer,
                  wrong: currentQuiz.answers[qIndex] !== null && currentQuiz.answers[qIndex] === oIndex && oIndex !== q.answer
                }"
                :disabled="currentQuiz.answers[qIndex] !== null"
                @click="selectOption(qIndex, oIndex)"
              >
                <span class="opt-tag">{{ ['A','B','C'][oIndex] }}</span>
                {{ opt }}
              </button>
            </div>
            <p v-if="currentQuiz.answers[qIndex] !== null && currentQuiz.answers[qIndex] !== q.answer" class="quiz-tip">
              解析：{{ q.tip }}
            </p>
          </div>

          <!-- 加号：追加额外题 -->
          <div v-if="quizDone" class="quiz-extra">
            <button class="quiz-add" :disabled="currentQuiz.extra.length >= 2" @click="addExtraQuestion">
              ＋ 加题
            </button>
            <div
              v-for="(eq, eqIndex) in currentQuiz.extra"
              :key="`extra-${eqIndex}`"
              class="quiz-q extra"
              :class="{ answered: currentQuiz.answers[5 + eqIndex] !== null }"
            >
              <p class="quiz-question">{{ 5 + eqIndex + 1 }}. {{ eq.q }}</p>
              <div class="quiz-options">
                <button
                  v-for="(opt, oIndex) in eq.options"
                  :key="oIndex"
                  class="quiz-opt"
                  :class="{
                    picked: currentQuiz.answers[5 + eqIndex] === oIndex,
                    correct: currentQuiz.answers[5 + eqIndex] !== null && oIndex === eq.answer,
                    wrong: currentQuiz.answers[5 + eqIndex] !== null && currentQuiz.answers[5 + eqIndex] === oIndex && oIndex !== eq.answer
                  }"
                  :disabled="currentQuiz.answers[5 + eqIndex] !== null"
                  @click="selectExtraAnswer(eqIndex, oIndex)"
                >
                  <span class="opt-tag">{{ ['A','B','C'][oIndex] }}</span>
                  {{ opt }}
                </button>
              </div>
              <p v-if="currentQuiz.answers[5 + eqIndex] !== null && currentQuiz.answers[5 + eqIndex] !== eq.answer" class="quiz-tip">
                解析：{{ eq.tip }}
              </p>
            </div>
          </div>
        </div>

        <button
          v-if="!isLast"
          class="skip-btn"
          @click="nextStep"
        >
          下一张 <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"/></svg>
        </button>
      </div>

    <!-- 底部圆点指示器 -->
    <div class="bottom-bar">
      <div class="dots">
        <button
          v-for="(slide, index) in slides"
          :key="index"
          class="dot"
          :class="{ active: index === currentStep, done: index < currentStep }"
          @click="goToStep(index)"
        >
          <span v-if="index === currentStep" class="dot-label">{{ slide.label }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.carousel {
  position: relative;
  width: 100%;
  height: 100dvh;
  overflow: hidden;
  background: #0d0d0d;
}

/* ============ 顶部导航 ============ */
.top-bar {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 30;
  background: linear-gradient(to bottom, rgba(0,0,0,0.6), transparent);
}
.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid rgba(255,255,255,0.25);
  background: rgba(255,255,255,0.1);
  backdrop-filter: blur(10px);
  color: rgba(255,255,255,0.85);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
}
.back-btn:hover { background: rgba(255,255,255,0.2); color: #fff; }
.brand { display: flex; align-items: center; gap: 8px; color: #fff; }
.brand-logo {
  width: 30px; height: 30px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #c41e3a, #8b0000);
  border-radius: 7px;
  color: #fff; font-weight: 700; font-size: 15px;
  box-shadow: 0 2px 10px rgba(196,30,58,0.4);
}
.brand-name { font-size: 17px; letter-spacing: 2px; font-family: 'ZhiMangXing','KaiTi',serif; }
.brand-sub { font-size: 13px; color: rgba(255,255,255,0.6); }
.page-num { font-size: 13px; color: rgba(255,255,255,0.5); font-variant-numeric: tabular-nums; }

/* ============ 进度条 ============ */
.progress-track {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: rgba(255,255,255,0.08);
  z-index: 31;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #d4a574, #c41e3a);
  transition: width 0.5s cubic-bezier(0.4,0,0.2,1);
  box-shadow: 0 0 8px rgba(212,165,116,0.5);
}

/* ============ 图片区域 ============ */
.image-area {
  position: absolute;
  top: 56px;
  left: 0;
  right: 0;
  bottom: 300px;
}
.slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.5s ease;
}
.slide.active { opacity: 1; z-index: 2; }
.slide-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.img-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.5), transparent 40%);
}
.img-title-area {
  position: absolute;
  top: 20px;
  left: 24px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.slide-label {
  display: inline-block;
  padding: 4px 12px;
  background: linear-gradient(135deg, #c41e3a, #8b0000);
  border-radius: 4px;
  font-size: 12px;
  color: #fff;
  letter-spacing: 1px;
  width: fit-content;
}
.slide-title {
  font-size: 28px;
  color: #fff;
  margin: 0;
  text-shadow: 0 2px 12px rgba(0,0,0,0.6);
  font-family: 'ZhiMangXing','KaiTi',serif;
  letter-spacing: 3px;
}
.audio-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
  padding: 4px 10px;
  border-radius: 12px;
  border: 1px solid rgba(212,165,116,0.4);
  background: rgba(212,165,116,0.15);
  color: #d4a574;
  font-size: 12px;
  cursor: pointer;
  width: fit-content;
  transition: all 0.3s;
}
.audio-btn:hover:not(:disabled) { background: rgba(212,165,116,0.3); color: #fff; }
.audio-btn.disabled {
  opacity: 0.6;
  border-style: dashed;
  cursor: not-allowed;
}
.audio-btn.playing {
  background: rgba(196,30,58,0.5);
  border-color: rgba(196,30,58,0.7);
  color: #fff;
}

/* ============ 左右箭头 ============ */
.arrow-btn {
  position: absolute;
  top: 40%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.2);
  background: rgba(0,0,0,0.4);
  backdrop-filter: blur(10px);
  color: rgba(255,255,255,0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 20;
  transition: all 0.3s;
}
.arrow-btn:hover {
  background: rgba(0,0,0,0.6);
  color: #fff;
  border-color: rgba(212,165,116,0.5);
  transform: translateY(-50%) scale(1.1);
}
.arrow-left { left: 24px; }
.arrow-right { right: 24px; }

/* ============ 小人解说员 ============ */
.narrator {
  position: absolute;
  bottom: 50px;
  left: 0;
  right: 0;
  display: flex;
  align-items: flex-end;
  gap: 16px;
  padding: 0 24px;
  z-index: 25;
  transition: bottom 0.3s ease;
}
/* 答题面板出现时，解说气泡上移到答题面板上方，避免被遮挡误触 */
.quiz-open .narrator {
  bottom: calc(44px + min(46vh, 420px) + 12px);
}
.narrator-avatar {
  position: relative;
  width: 90px;
  height: 120px;
  flex-shrink: 0;
}
.narrator-avatar img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  filter: drop-shadow(0 4px 16px rgba(0,0,0,0.4));
  animation: float 3s ease-in-out infinite;
}
.avatar-glow {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 70px;
  height: 12px;
  background: radial-gradient(ellipse, rgba(212,165,116,0.3), transparent 70%);
  border-radius: 50%;
  animation: glow 3s ease-in-out infinite;
}
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
@keyframes glow {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 0.3; }
}

.speech-bubble {
  flex: 1;
  position: relative;
  padding: 14px 18px;
  background: rgba(26, 26, 46, 0.94);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(212, 165, 116, 0.3);
  border-radius: 16px;
  margin-bottom: 8px;
}

/* ============ 答题面板（独立可滚动，选项可点击） ============
   放在解说气泡下方、底部圆点上方，高度加高，避免第 5 题误触到解说文字 */
.quiz-panel {
  position: absolute;
  left: 24px;
  right: 24px;
  bottom: 44px;
  z-index: 26;
  max-height: min(46vh, 420px);
  overflow-y: auto;
  padding: 14px 18px;
  background: rgba(26, 26, 46, 0.97);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(212, 165, 116, 0.35);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.55);
}
.bubble-tail {
  position: absolute;
  bottom: 20px;
  left: -10px;
  width: 0;
  height: 0;
  border-top: 8px solid transparent;
  border-bottom: 8px solid transparent;
  border-right: 12px solid rgba(212, 165, 116, 0.3);
}
.speech-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.92);
  letter-spacing: 0.5px;
}
.cursor {
  color: #d4a574;
  animation: blink 0.8s step-end infinite;
  font-weight: bold;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ============ 答题区 ============ */
.quiz-box {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(212, 165, 116, 0.2);
}
.quiz-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.quiz-title {
  color: #d4a574;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
}
.quiz-progress {
  color: rgba(255,255,255,0.5);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.quiz-score {
  color: #6fd08c;
  font-size: 12px;
  font-weight: 600;
}
.quiz-score:not(.pass) {
  color: #f0a8a8;
}
.quiz-result {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px 14px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(111,208,140,0.15), rgba(212,165,116,0.1));
  border: 1px solid rgba(111,208,140,0.35);
}
.quiz-result:not(.pass) {
  background: linear-gradient(135deg, rgba(240,130,130,0.15), rgba(212,165,116,0.1));
  border-color: rgba(240,130,130,0.4);
}
.quiz-result-label {
  color: rgba(255,255,255,0.6);
  font-size: 12px;
}
.quiz-result-num {
  font-size: 26px;
  font-weight: 800;
  color: #6fd08c;
  font-variant-numeric: tabular-nums;
}
.quiz-result:not(.pass) .quiz-result-num { color: #f0a8a8; }
.quiz-result-num i {
  font-style: normal;
  font-size: 13px;
  font-weight: 600;
  margin-left: 2px;
}
.quiz-result-verdict {
  flex: 1;
  color: rgba(255,255,255,0.85);
  font-size: 12px;
  letter-spacing: 0.5px;
}
.quiz-q {
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(255,255,255,0.04);
  margin-bottom: 8px;
  transition: background 0.3s;
}
.quiz-q.answered {
  background: rgba(255,255,255,0.07);
}
.quiz-q.extra {
  background: rgba(108, 90, 205, 0.12);
}
.quiz-question {
  margin: 0 0 6px;
  color: rgba(255,255,255,0.95);
  font-size: 13px;
  line-height: 1.5;
  font-weight: 500;
}
.quiz-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}
.quiz-opt {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 8px;
  border: 1px solid rgba(255,255,255,0.15);
  background: rgba(255,255,255,0.05);
  color: rgba(255,255,255,0.85);
  font-size: 12px;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s;
}
.quiz-opt:hover:not(:disabled) {
  border-color: rgba(212,165,116,0.6);
  background: rgba(212,165,116,0.15);
}
.quiz-opt:disabled { cursor: default; }
.opt-tag {
  display: grid;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 50%;
  background: rgba(255,255,255,0.15);
  font-size: 11px;
  font-weight: 700;
}
.quiz-opt.picked {
  border-color: rgba(212,165,116,0.8);
  background: rgba(212,165,116,0.25);
}
.quiz-opt.correct {
  border-color: rgba(111,208,140,0.8);
  background: rgba(111,208,140,0.2);
  color: #a5f0b8;
}
.quiz-opt.correct .opt-tag { background: #6fd08c; color: #0d3320; }
.quiz-opt.wrong {
  border-color: rgba(240,130,130,0.8);
  background: rgba(240,130,130,0.2);
  color: #ffb3b3;
}
.quiz-opt.wrong .opt-tag { background: #f08282; color: #3d0d0d; }
.quiz-tip {
  margin: 6px 0 0;
  padding: 6px 8px;
  border-radius: 6px;
  background: rgba(212,165,116,0.1);
  border-left: 3px solid rgba(212,165,116,0.5);
  color: rgba(255,255,255,0.75);
  font-size: 11px;
  line-height: 1.5;
}
.quiz-extra {
  margin-top: 4px;
}
.quiz-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: 14px;
  border: 1px dashed rgba(212,165,116,0.5);
  background: rgba(212,165,116,0.1);
  color: #d4a574;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.quiz-add:hover:not(:disabled) {
  background: rgba(212,165,116,0.25);
  border-style: solid;
}
.quiz-add:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.skip-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 4px 12px;
  border-radius: 12px;
  border: 1px solid rgba(212,165,116,0.4);
  background: rgba(212,165,116,0.15);
  color: #d4a574;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
}
.skip-btn:hover {
  background: rgba(212,165,116,0.3);
  color: #fff;
}

/* ============ 底部圆点 ============ */
.bottom-bar {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  padding: 12px 24px;
  z-index: 25;
  background: linear-gradient(to top, rgba(0,0,0,0.7), transparent);
  display: flex;
  justify-content: center;
}
.dots {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  justify-content: center;
  max-width: 95%;
}
.dot {
  min-width: 7px;
  height: 7px;
  border-radius: 4px;
  border: none;
  background: rgba(255,255,255,0.2);
  cursor: pointer;
  transition: all 0.3s;
  padding: 0 6px;
  overflow: hidden;
  font-size: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dot.done { background: rgba(212,165,116,0.5); }
.dot.active {
  background: linear-gradient(135deg, #d4a574, #c41e3a);
  min-width: auto;
  height: 22px;
  padding: 0 12px;
  border-radius: 11px;
  box-shadow: 0 2px 10px rgba(196,30,58,0.4);
}
.dot-label {
  font-size: 11px;
  color: #fff;
  font-weight: 500;
  white-space: nowrap;
}
.dot:hover:not(.active) { background: rgba(255,255,255,0.4); }

/* ============ 响应式 ============ */
@media (max-width: 768px) {
  .top-bar { padding: 0 12px; height: 48px; }
  .brand-name, .brand-sub { display: none; }
  .image-area { bottom: 260px; top: 48px; }
  .arrow-btn { width: 36px; height: 36px; }
  .arrow-left { left: 8px; }
  .arrow-right { right: 8px; }
  .narrator { padding: 0 12px; bottom: 44px; gap: 10px; }
  .narrator-avatar { width: 60px; height: 80px; }
  .speech-bubble { padding: 12px 14px; max-height: 200px; overflow-y: auto; }
  .speech-text { font-size: 12px; line-height: 1.6; }
  .quiz-options { grid-template-columns: 1fr; }
  .quiz-panel {
    left: 12px;
    right: 12px;
    bottom: 44px;
    max-height: min(50vh, 380px);
  }
  .quiz-open .narrator {
    bottom: calc(44px + min(50vh, 380px) + 10px);
  }
  .slide-title { font-size: 22px; }
  .dot.active { height: 20px; padding: 0 10px; }
  .dot-label { font-size: 10px; }
}
</style>
