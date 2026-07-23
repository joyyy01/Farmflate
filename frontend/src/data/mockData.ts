import type { Province, CommunityPost, RecommendedCrop, MyFieldItem, CropOption } from '../types/farmflate';

export const PROVINCES_DATA: Province[] = [
  { id: 'seoul', name: '서울특별시', districts: ['종로구', '중구', '용산구', '성동구', '강남구'] },
  { id: 'gyeonggi', name: '경기도', districts: ['수원시', '고양시', '용인시', '성남시', '화성시', '평택시', '파주시'] },
  { id: 'chungbuk', name: '충청북도', districts: ['청주시', '충주시', '제천시', '보은군', '옥천군', '음성군'] },
  { id: 'chungnam', name: '충청남도', districts: ['천안시', '공주시', '보령시', '아산시', '서산시', '논산시', '당진시'] },
  { id: 'gyeongbuk', name: '경상북도', districts: ['포항시', '경주시', '김천시', '안동시', '구미시', '영주시', '상주시'] },
  { id: 'gyeongnam', name: '경상남도', districts: ['창원시', '진주시', '통영시', '사천시', '김해시', '밀양시', '거제시'] },
  { id: 'jeonnam', name: '전라남도', districts: ['목포시', '여수시', '순천시', '나주시', '광양시', '해남군', '영암군'] },
  {
    id: 'jeonbuk',
    name: '전북특별자치도',
    districts: ['고창군', '전주시', '군산시', '익산시', '정읍시', '남원시', '김제시', '완주군', '진안군', '무주군', '장수군', '임실군', '순창군', '부안군']
  },
  { id: 'gangwon', name: '강원특별자치도', districts: ['춘천시', '원주시', '강릉시', '동해시', '태백시', '속초시', '삼척시'] },
  { id: 'jeju', name: '제주특별자치도', districts: ['제주시', '서귀포시'] },
  { id: 'sejong', name: '세종특별자치시', districts: ['세종시'] },
  { id: 'incheon', name: '인천광역시', districts: ['중구', '동구', '미추홀구', '연수구', '남동구', '부평구', '계양구', '서구'] },
  { id: 'daejeon', name: '대전광역시', districts: ['동구', '중구', '서구', '유성구', '대덕구'] },
  { id: 'ulsan', name: '울산광역시', districts: ['중구', '남구', '동구', '북구', '울주군'] },
  { id: 'daegu', name: '대구광역시', districts: ['중구', '동구', '서구', '남구', '북구', '수성구', '달서구', '달성군'] },
  { id: 'busan', name: '부산광역시', districts: ['중구', '서구', '동구', '영도구', '부산진구', '동래구', '남구', '북구', '해운대구'] },
  { id: 'gwangju', name: '광주광역시', districts: ['동구', '서구', '남구', '북구', '광산구'] }
];

export const MOCK_MY_FIELDS: MyFieldItem[] = [
  {
    id: 'field_1',
    fieldName: '상추밭',
    cropName: '상추',
    daysPlanted: 18,
    stage: '생장 단계',
    statusBadge: '물주기 필요',
    statusBadgeColor: 'yellow',
    todayTask: '오늘 물주기 필요',
    reportTime: '오늘 06:00 자동 분석됨'
  },
  {
    id: 'field_2',
    fieldName: '오이밭',
    cropName: '오이',
    daysPlanted: 32,
    stage: '개화 단계',
    statusBadge: '확인 필요',
    statusBadgeColor: 'blue',
    todayTask: '지지대 확인 필요',
    reportTime: '오늘 08:00 자동 분석됨'
  }
];

export const MOCK_CROP_OPTIONS: CropOption[] = [
  { id: 'apple', name: '사과' },
  { id: 'pear', name: '배' },
  { id: 'cucumber', name: '오이' },
  { id: 'potato', name: '감자' },
  { id: 'lettuce', name: '상추' }
];

export const MOCK_COMMUNITY_POSTS: CommunityPost[] = [
  {
    id: 'post_1',
    category: '질문/답변',
    tagLocation: '전북 고창군',
    title: '감자 잎이 노랗게 변하는데 정상인가요?',
    content: '이번에 처음 심었는데 아랫잎부터 노랗게 변해서 걱정이예요...',
    commentCount: 12,
    likeCount: 8,
    author: '초보농부',
    timeAgo: '2시간 전',
    isLiked: false,
    isSaved: false,
    comments: [
      { id: 'c1', author: '고창베테랑', content: '장마 후 배수로 수심 체크해보세요! 밭골에 물 고이면 아랫잎부터 습해 증상이 옵니다.', timeAgo: '1시간 전' },
      { id: 'c2', author: '전주농업인', content: '토양 pH가 낮아도 칼슘 흡수가 저하돼요. 가까운 농업기술센터 무료 검정 받아보시길 추천합니다.', timeAgo: '30분 전' }
    ]
  },
  {
    id: 'post_2',
    category: '농가 노하우',
    tagLocation: '경북 상주시',
    title: '상주시 농업기술센터 무료 토양검정 신청받는대요',
    content: '이번 달 말까지 선착순으로 신청받는다고 하네요, 링크 공유합니다.',
    commentCount: 20,
    likeCount: 31,
    author: '상주포도농가',
    timeAgo: '5시간 전',
    isLiked: true,
    isSaved: true,
    comments: [
      { id: 'c3', author: '익산농가', content: '좋은 정보 감사합니다! 올해 이대로 신청해 봐야겠네요.', timeAgo: '4시간 전' }
    ]
  },
  {
    id: 'post_3',
    category: '장터',
    tagLocation: '장터',
    title: '상추 모종 여유분 나눔합니다 (고창)',
    content: '심고 남은 상추 모종 있으신 분 나눔 원해요. 댓글 주세요!',
    commentCount: 6,
    likeCount: 15,
    author: '귀농인',
    timeAgo: '1일 전',
    isLiked: false,
    isSaved: false,
    comments: []
  }
];

export const MOCK_RECOMMENDED_CROPS: RecommendedCrop[] = [
  {
    id: 'crop_1',
    cropName: '고창 황토 고구마',
    suitabilityScore: 94,
    matchReason: '배수가 우수한 황토 토양 및 강수량 조건 최적 적합',
    iconName: 'Potato',
    soilSuitability: '약산성 pH 6.0 ~ 6.5 (적합)',
    climateRisk: '장마철 과습 주의 필요'
  }
];
