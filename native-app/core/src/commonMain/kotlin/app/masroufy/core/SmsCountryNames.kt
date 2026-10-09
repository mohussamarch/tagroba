package app.masroufy.core

/**
 * **بيانات البلاد** لفحص «المحل برّه البلد» (`SmsMerchantCountry.kt` — الجولة التامنة، المراجعة العدائية الرابعة): القايمة القديمة كانت قصيرة
 * (حوالي 80 كود و20 اسم)، فـ«SAMPLE RESORT BAKU AZERBAIJAN» · «… KUALA LUMPUR MALAYSIA» · «فندق العينة تبليسي جورجيا» · «… ZANZIBAR TZ» ·
 * «TEST STORE DUBAI» (مدينة من غير بلد) كانوا بيتسجلوا شراء محلي لوحدهم (§75-12). دلوقتي: **كل** أكواد ISO 3166-1 (حرفين وتلاتة) ما عدا
 * الأكواد اللي هي كلمة أو اختصار شائع في آخر اسم محل (اختيار (ذ) في §72.4) · أسامي البلاد بالإنجليزي والعربي · ومدن معروفة (قايمة مقفولة).
 */

/** ISO 3166-1 alpha-2 كلها (+ UK) — من غير الكلمات/الاختصارات: AD AI AM AS AT BE BY CO DO IS ME NO SO TO MD PM ST TV FM AG BV KG NA TM CC MM PR SR RE IO MS NE. */
internal val ISO_ALPHA2: Set<String> = (
    "AE AF AL AO AQ AR AU AW AX AZ BA BB BD BF BG BH BI BJ BL BM BN BO BQ BR BS BT BW BZ CA CD CF CG CH CI CK CL CM CN CR CU CV CW CX CY CZ DE DJ DK " +
        "DM DZ EC EE EG EH ER ES ET FI FJ FK FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IQ IR IT JE " +
        "JM JO JP KE KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MF MG MH MK ML MN MO MP MQ MR MT MU MV MW MX MY MZ NC NF NG NI NL " +
        "NP NR NU NZ OM PA PE PF PG PH PK PL PN PS PT PW PY QA RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SS SV SX SY SZ TC TD TF TG TH TJ TK TL TN " +
        "TR TT TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW UK"
    ).split(' ').toSet()

/** ISO 3166-1 alpha-3 كلها (+ KSA · UAE) — من غير الكلمات: AND ARM BEN CAN COD COM CUB DOM EST GIN GUY MAC NAM NIC PAN PER PNG TON VAT ALA ATA SUR LIE GUM JAM. */
internal val ISO_ALPHA3: Set<String> = (
    "AFG ALB DZA ASM AGO AIA ATG ARG ABW AUS AUT AZE BHS BHR BGD BRB BLR BEL BLZ BMU BTN BOL BES BIH BWA BVT BRA IOT BRN BGR BFA BDI CPV KHM CMR CYM " +
        "CAF TCD CHL CHN CXR CCK COL COG COK CRI CIV HRV CUW CYP CZE DNK DJI DMA ECU EGY SLV GNQ ERI SWZ ETH FLK FRO FJI FIN FRA GUF PYF ATF GAB GMB GEO " +
        "DEU GHA GIB GRC GRL GRD GLP GTM GGY GNB HTI HMD HND HKG HUN ISL IND IDN IRN IRQ IRL IMN ISR ITA JPN JEY JOR KAZ KEN KIR PRK KOR KWT KGZ LAO LVA " +
        "LBN LSO LBR LBY LTU LUX MDG MWI MYS MDV MLI MLT MHL MTQ MRT MUS MYT MEX FSM MDA MCO MNG MNE MSR MAR MOZ MMR NRU NPL NLD NCL NZL NER NGA NIU NFK " +
        "MKD MNP NOR OMN PAK PLW PSE PRY PHL PCN POL PRT PRI QAT REU ROU RUS RWA BLM SHN KNA LCA MAF SPM VCT WSM SMR STP SAU SEN SRB SYC SLE SGP SXM SVK " +
        "SVN SLB SOM ZAF SGS SSD ESP LKA SDN SJM SWE CHE SYR TWN TJK TZA THA TLS TGO TKL TTO TUN TUR TKM TCA TUV UGA UKR ARE GBR USA UMI URY UZB VUT VEN " +
        "VNM VGB VIR WLF ESH YEM ZMB ZWE KSA UAE"
    ).split(' ').toSet()

/** أسامي البلاد (إنجليزي وعربي) — بتتقارن بعد [placeKey]. */
private val COUNTRY_NAMES: List<String> = (
    "afghanistan|albania|algeria|andorra|angola|argentina|armenia|australia|austria|azerbaijan|bahamas|bahrain|bangladesh|barbados|belarus|belgium" +
        "|belize|benin|bhutan|bolivia|bosnia|bosnia and herzegovina|botswana|brazil|brunei|bulgaria|burkina faso|burundi|cambodia|cameroon|canada" +
        "|cape verde|chile|china|colombia|comoros|congo|costa rica|croatia|cuba|cyprus|czech republic|czechia|denmark|djibouti|dominican republic" +
        "|ecuador|egypt|el salvador|eritrea|estonia|eswatini|ethiopia|fiji|finland|france|gabon|gambia|georgia|germany|ghana|greece|guatemala|guinea" +
        "|haiti|honduras|hong kong|hungary|iceland|india|indonesia|iran|iraq|ireland|israel|italy|jamaica|japan|jordan|kazakhstan|kenya|kosovo|kuwait" +
        "|kyrgyzstan|laos|latvia|lebanon|lesotho|liberia|libya|liechtenstein|lithuania|luxembourg|macau|macao|madagascar|malawi|malaysia|maldives" +
        "|malta|mauritania|mauritius|mexico|moldova|monaco|mongolia|montenegro|morocco|mozambique|myanmar|namibia|nepal|netherlands|holland" +
        "|new zealand|nicaragua|nigeria|north macedonia|macedonia|norway|oman|pakistan|palestine|panama|papua new guinea|paraguay|peru|philippines" +
        "|poland|portugal|qatar|romania|russia|rwanda|saudi arabia|senegal|serbia|seychelles|sierra leone|singapore|slovakia|slovenia|somalia" +
        "|south africa|south korea|korea|spain|sri lanka|sudan|south sudan|suriname|sweden|switzerland|syria|taiwan|tajikistan|tanzania|thailand" +
        "|tunisia|turkey|turkiye|turkmenistan|uganda|ukraine|united arab emirates|emirates|united kingdom|great britain|britain|england|scotland" +
        "|united states|united states of america|america|uruguay|uzbekistan|venezuela|vietnam|viet nam|yemen|zambia|zimbabwe" +
        "|افغانستان|البانيا|الجزائر|انغولا|الارجنتين|ارمينيا|استراليا|النمسا|اذربيجان|البحرين|بنغلاديش|بنجلاديش|بيلاروسيا|بلجيكا|بوليفيا|البوسنه" +
        "|البوسنه والهرسك|البرازيل|بروناي|بلغاريا|كمبوديا|الكاميرون|كندا|تشاد|تشيلي|الصين|كولومبيا|جزر القمر|الكونغو|كرواتيا|كوبا|قبرص|التشيك" +
        "|الدنمارك|جيبوتي|الاكوادور|مصر|اريتريا|استونيا|اثيوبيا|فنلندا|فرنسا|الغابون|جورجيا|المانيا|غانا|اليونان|هونغ كونغ|هونج كونج|المجر|ايسلندا" +
        "|الهند|اندونيسيا|ايران|العراق|ايرلندا|ايطاليا|اليابان|الاردن|كازاخستان|كينيا|كوسوفو|الكويت|قيرغيزستان|لاوس|لاتفيا|لبنان|ليبيا|ليتوانيا" +
        "|لوكسمبورغ|مدغشقر|ماليزيا|المالديف|جزر المالديف|مالطا|موريتانيا|موريشيوس|المكسيك|مولدوفا|موناكو|منغوليا|الجبل الاسود|المغرب|موزمبيق" +
        "|ميانمار|ناميبيا|نيبال|هولندا|نيوزيلندا|النيجر|نيجيريا|مقدونيا|النرويج|عمان|سلطنه عمان|باكستان|فلسطين|بنما|الباراغواي|بيرو|الفلبين|بولندا" +
        "|البرتغال|قطر|رومانيا|روسيا|رواندا|السنغال|صربيا|سنغافوره|سلوفاكيا|سلوفينيا|الصومال|جنوب افريقيا|كوريا|كوريا الجنوبيه|اسبانيا|سريلانكا" +
        "|سري لانكا|السودان|السويد|سويسرا|سوريا|تايوان|طاجيكستان|تنزانيا|تايلاند|تايلند|توغو|تونس|تركيا|تركمانستان|اوغندا|اوكرانيا|الامارات" +
        "|الامارات العربيه المتحده|بريطانيا|المملكه المتحده|انجلترا|امريكا|الولايات المتحده|الولايات المتحده الامريكيه|الاوروغواي|اوزبكستان|فنزويلا" +
        "|فيتنام|اليمن|زامبيا|زيمبابوي|ksa|uae|usa|السعوديه|المملكه العربيه السعوديه"
    ).split('|')

/** مدن معروفة (قايمة مقفولة) — «TEST STORE DUBAI» · «… ISTANBUL» · «… MAKKAH». المدن اللي اسمها كلمة عادية في أسامي المحلات («المدينة» · «مكة» لوحدها في مصر) مش هنا. */
private val CITY_NAMES: List<String> = (
    "dubai|abu dhabi|sharjah|ajman|fujairah|ras al khaimah|doha|manama|muscat|salalah|kuwait city|amman|aqaba|beirut|damascus|baghdad|erbil" +
        "|istanbul|ankara|antalya|izmir|bursa|trabzon|bodrum|london|manchester|birmingham|edinburgh|paris|lyon|rome|milan|venice|florence|madrid" +
        "|barcelona|berlin|munich|frankfurt|vienna|geneva|zurich|interlaken|amsterdam|brussels|prague|budapest|athens|lisbon|baku|tbilisi|batumi" +
        "|yerevan|moscow|kuala lumpur|penang|langkawi|bangkok|phuket|pattaya|bali|jakarta|tokyo|osaka|seoul|beijing|shanghai|guangzhou|shenzhen" +
        "|delhi|new delhi|mumbai|karachi|lahore|islamabad|dhaka|colombo|kathmandu|new york|los angeles|las vegas|miami|chicago|toronto|sydney" +
        "|melbourne|marrakech|marrakesh|casablanca|tunis|sarajevo|zanzibar|nairobi|addis ababa|khartoum|tripoli|sanaa|aden" +
        "|دبي|ابوظبي|ابو ظبي|الشارقه|عجمان|الدوحه|المنامه|مسقط|صلاله|بيروت|دمشق|بغداد|اربيل|اسطنبول|انقره|انطاليا|ازمير|طرابزون|لندن|مانشستر" +
        "|باريس|روما|ميلانو|مدريد|برشلونه|برلين|ميونخ|فيينا|جنيف|زيورخ|امستردام|بروكسل|براغ|اثينا|لشبونه|باكو|تبليسي|باتومي|موسكو|كوالالمبور" +
        "|كوالا لمبور|بانكوك|بوكيت|بالي|جاكرتا|طوكيو|سيول|بكين|شنغهاي|دلهي|نيودلهي|مومباي|كراتشي|لاهور|اسلام اباد|دكا|كولومبو|نيويورك|تورنتو" +
        "|سيدني|مراكش|الدار البيضاء|سراييفو|زنجبار|نيروبي|الخرطوم|طرابلس|صنعاء|عدن"
    ).split('|')

/** مدن السعودية — محلية في قارئ السعودية، وبرّه في قارئ مصر. */
internal val SAUDI_CITIES: List<String> = (
    "riyadh|jeddah|jiddah|makkah|mecca|makkah al mukarramah|madinah|medina|al madinah|madinah al munawwarah|dammam|khobar|al khobar|dhahran|taif" +
        "|abha|tabuk|buraidah|hail|jazan|najran|yanbu|jubail|al ula|alula|الرياض|جده|مكه المكرمه|المدينه المنوره|الدمام|الظهران|الطائف|ابها|تبوك" +
        "|بريده|حائل|جازان|نجران|ينبع|الجبيل|العلا"
    ).split('|')

/** مدن مصر — محلية في قارئ مصر، وبرّه في قارئ السعودية. */
internal val EGYPT_CITIES: List<String> = (
    "cairo|giza|alexandria|sharm el sheikh|sharm|hurghada|luxor|aswan|القاهره|الجيزه|الاسكندريه|شرم الشيخ|الغردقه|الاقصر|اسوان"
    ).split('|')

/** المفتاح للمقارنة: [shapeKey] + «ة» = «ه» (كتابتين لنفس الكلمة). */
internal fun placeKey(text: String): String = shapeKey(text).replace('ة', 'ه')

/** كل الأماكن المعروفة (بلد أو مدينة) بعد [placeKey]. */
internal val KNOWN_PLACES: Set<String> = (COUNTRY_NAMES + CITY_NAMES + SAUDI_CITIES + EGYPT_CITIES).map(::placeKey).toSet()
