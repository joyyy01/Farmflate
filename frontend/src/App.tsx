import { useEffect, useRef, useState } from 'react';
import type { ViewStep, TabState, CommunityPost } from './types/farmflate';
import { SplashView } from './components/farmflate/SplashView';
import { LandingView } from './components/farmflate/LandingView';
import { RegionExploreView } from './components/farmflate/RegionExploreView';
import { AnalyzingView } from './components/farmflate/AnalyzingView';
import { RegionReportSummaryView } from './components/farmflate/RegionReportSummaryView';
import { RegionRisksView } from './components/farmflate/RegionRisksView';
import { RegionTipsView } from './components/farmflate/RegionTipsView';
import { RecommendedCropsView } from './components/farmflate/RecommendedCropsView';
import { CropSuitabilityReportView } from './components/farmflate/CropSuitabilityReportView';
import { CropConditionInputView, type CropRegistrationInput } from './components/farmflate/CropConditionInputView';
import { MainDashboardView } from './components/farmflate/MainDashboardView';
import { MyFieldListView } from './components/farmflate/MyFieldListView';
import { FieldDashboardView } from './components/farmflate/FieldDashboardView';
import { CommunityListView } from './components/farmflate/CommunityListView';
import { CommunityCreatePostView } from './components/farmflate/CommunityCreatePostView';
import { MyPageView } from './components/farmflate/MyPageView';
import { ApiError, ApiService } from './services/api';
import type { FieldProfile, HomeData, RegionAnalysisRequest, RegionReport } from './services/api';
import { canOpenReport, stateFromAnalysisStatus, type AnalysisState } from './services/reportLifecycle';
import { AIChatModal } from './components/farmflate/AIChatModal';
import { useDailyRefresh } from './hooks/useDailyRefresh';

type ExtendedViewStep = ViewStep | 'splash';

const normalizeCommunityPosts = (data: unknown): CommunityPost[] => {
  if (!Array.isArray(data)) throw new ApiError(200, 'MALFORMED_COMMUNITY_POSTS', '게시글 목록 응답이 올바르지 않습니다.', data, false);
  return data.map((item): CommunityPost => {
    if (!item || typeof item !== 'object') throw new ApiError(200, 'MALFORMED_COMMUNITY_POSTS', '게시글 항목이 올바르지 않습니다.', item, false);
    const post = item as Record<string, unknown>;
    const id = typeof post.id === 'string' || typeof post.id === 'number' ? String(post.id) : '';
    const title = typeof post.title === 'string' ? post.title : '';
    if (!id || !title || typeof post.category !== 'string' || typeof post.tagLocation !== 'string' || typeof post.content !== 'string' || typeof post.author !== 'string' || typeof post.likeCount !== 'number' || typeof post.commentCount !== 'number') {
      throw new ApiError(200, 'MALFORMED_COMMUNITY_POSTS', '게시글 항목이 올바르지 않습니다.', item, false);
    }
    return {
      id,
      category: post.category,
      tagLocation: post.tagLocation,
      title,
      content: post.content,
      author: post.author,
      timeAgo: typeof post.timeAgo === 'string' ? post.timeAgo : '시간 정보 없음',
      commentCount: post.commentCount,
      likeCount: post.likeCount,
      isLiked: typeof post.isLiked === 'boolean' ? post.isLiked : false,
      isSaved: typeof post.isSaved === 'boolean' ? post.isSaved : false,
      imageUrl: typeof post.imageUrl === 'string' ? post.imageUrl : undefined,
      comments: Array.isArray(post.comments) ? post.comments as CommunityPost['comments'] : []
    };
  });
};

// TEMP: mock data for MyFieldListView design work. Remove once the real
// /api/fields response is what you want to design against.
const MOCK_FIELDS: FieldProfile[] = [
  {
    id: 'mock-field-1',
    fieldName: '우리집 텃밭',
    cropCode: 'POTATO',
    cropName: '감자',
    cultivationMethod: '노지',
    cultivationStartDate: '2026-05-12',
    stage: 'growing',
    active: true,
    suitabilityReport: {
      suitabilityScore: 88,
      grade: 'A',
      summary: '이번 주는 배수 관리에 신경 쓰면 좋아요. 토양 수분이 다소 높은 편입니다.',
      analysisBasisDate: '2026-07-20',
      conditions: [],
      keyRisks: [],
      prePlantChecklist: [],
      currentManagementPoints: []
    },
    latestReport: {
      id: 'mock-report-1',
      fieldId: 'mock-field-1',
      reportDate: '2026-07-24',
      generatedAt: '2026-07-24T06:00:00+09:00',
      generationReason: '일일 자동 분석',
      suitabilityScore: 88,
      summary: '오늘은 물주기보다 배수로 확인이 우선이에요.',
      prioritizedActions: [],
      keyRisks: [],
      conditions: []
    }
  },
  {
    id: 'mock-field-2',
    fieldName: '뒷마당 상추밭',
    cropCode: 'LETTUCE',
    cropName: '상추',
    cultivationMethod: '노지',
    cultivationStartDate: '2026-06-01',
    stage: 'before',
    active: true,
    suitabilityReport: {
      suitabilityScore: 72,
      grade: 'B',
      summary: '아직 정식 전이라 발아 온도 유지가 중요해요.',
      analysisBasisDate: '2026-07-20',
      conditions: [],
      keyRisks: [],
      prePlantChecklist: [],
      currentManagementPoints: []
    },
    latestReport: null
  },
  {
    id: 'mock-field-3',
    fieldName: '옥상 텃밭',
    cropCode: 'PEPPER',
    cropName: '고추',
    cultivationMethod: '화분',
    cultivationStartDate: '2026-06-20',
    stage: 'growing',
    active: true,
    suitabilityReport: null,
    latestReport: null
  }
];

// TEMP: mock completed region analysis so "농작물 등록하기" doesn't bounce to
// the region-select screen in preview mode (crop registration requires a
// completed apiReport/analysisState). Remove alongside MOCK_FIELDS.
const MOCK_REGION_REPORT: RegionReport = {
  analysisId: 'mock-analysis-1',
  status: 'COMPLETED',
  dataMode: 'AUTO',
  analyzedAt: '2026-07-24T06:00:00+09:00',
  region: {
    sidoCode: '52',
    sidoName: '전북특별자치도',
    sigunguCode: '52790',
    sigunguName: '고창군'
  },
  location: null,
  baseFitness: 82,
  seasonReadiness: 78,
  dataConfidence: { score: 0.86, level: 'HIGH', message: null, range: null },
  regionScore: 82,
  summary: '전반적으로 노지 재배에 안정적인 환경입니다.',
  components: {
    climate: { score: 84, grade: 'A' },
    soil: { score: 79, grade: 'B' },
    hazard: { safetyScore: 88, grade: 'A' },
    cultivation: { score: 80, grade: 'B' }
  },
  environmentFeatures: ['배수 양호', '일조량 충분'],
  recommendedCrops: [
    { cropCode: 'POTATO', cropName: '감자', score: 88, rank: 1, positiveReasons: ['배수 양호', '서늘한 기후 적합'], cautionReason: null, category: '근채류', iconUrl: null },
    { cropCode: 'LETTUCE', cropName: '상추', score: 72, rank: 2, positiveReasons: ['일조량 충분'], cautionReason: '고온기 발아 주의', category: '엽채류', iconUrl: null },
    { cropCode: 'PEPPER', cropName: '고추', score: 68, rank: 3, positiveReasons: ['배수 양호'], cautionReason: '장마철 습해 주의', category: '과채류', iconUrl: null }
  ],
  cropResults: undefined,
  topRisks: [],
  safeWorkWindows: [],
  prioritizedActions: [],
  tips: [],
  sources: [],
  missingMetrics: []
};

export function App() {
  const checkHasToken = () => !!(localStorage.getItem('jwtToken') || localStorage.getItem('token'));
  const [viewStep, setViewStep] = useState<ExtendedViewStep>('splash');
  const [activeTab, setActiveTab] = useState<TabState>('home');
  const [isAIChatOpen, setIsAIChatOpen] = useState(false);
  const [isPreviewMode, setIsPreviewMode] = useState(false);

  /* Explore Screen Context (Fresh Region Analysis vs Changing an Existing Selection) */
  const [exploreMode, setExploreMode] = useState<'analyze' | 'change'>('analyze');

  /* Where the Region Tips screen's back button should return to */
  const [tipsReturnStep, setTipsReturnStep] = useState<ExtendedViewStep>('report_risks');

  /* Whether the report_summary -> report_risks -> report_tips chain was entered
     fresh (onboarding) or by re-viewing an already-analyzed report from the dashboard */
  const [reportFlowSource, setReportFlowSource] = useState<'onboarding' | 'view'>('onboarding');

  /* Clean Unauthenticated Default States (No Hardcoded Private Names) */
  const [isNewUser, setIsNewUser] = useState<boolean>(() => {
    if (!checkHasToken()) return true;
    const cached = localStorage.getItem('farmflate_is_new_user');
    return cached !== null ? JSON.parse(cached) : true;
  });

  const [userName, setUserName] = useState<string>(() => {
    if (!checkHasToken()) return '사용자님';
    return localStorage.getItem('farmflate_user_name') || '사용자님';
  });

  const [userEmail, setUserEmail] = useState<string>(() => {
    if (!checkHasToken()) return '미인증 계정';
    return localStorage.getItem('farmflate_user_email') || '미인증 계정';
  });

  const [selectedProvince, setSelectedProvince] = useState<string>(() => {
    return localStorage.getItem('farmflate_province') || '';
  });

  const [selectedDistrict, setSelectedDistrict] = useState<string>(() => {
    return localStorage.getItem('farmflate_district') || '';
  });

  const [selectedCropName, setSelectedCropName] = useState<string>('감자');

  const [apiReport, setApiReport] = useState<RegionReport | null>(null);
  const [analysisState, setAnalysisState] = useState<AnalysisState>({ kind: 'IDLE' });
  const [lastAnalysisRequest, setLastAnalysisRequest] = useState<RegionAnalysisRequest | null>(null);
  const [pendingCropRegistration, setPendingCropRegistration] = useState<CropRegistrationInput | null>(null);
  const [isFieldRegistrationFlow, setIsFieldRegistrationFlow] = useState(false);
  const [homeData, setHomeData] = useState<HomeData | null>(null);
  const [myFields, setMyFields] = useState<FieldProfile[]>(MOCK_FIELDS);
  const [selectedField, setSelectedField] = useState<FieldProfile | null>(null);
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [homeLoadError, setHomeLoadError] = useState<string | null>(null);
  const [fieldLoadError, setFieldLoadError] = useState<string | null>(null);
  const [communityLoadError, setCommunityLoadError] = useState<string | null>(null);
  const [communityComposeError, setCommunityComposeError] = useState<string | null>(null);
  const pollTimerRef = useRef<number | null>(null);
  const previewModeRef = useRef(false);
  const isAddingFieldRef = useRef(false);

  /* Full 100% Reliable Logout Reset Handler */
  const handleLogout = () => {
    // 1. Wipe all local storage & tokens
    localStorage.clear();

    // 2. Reset all React states to clean neutral values
    setUserName('사용자님');
    setUserEmail('미인증 계정');
    setSelectedProvince('');
    setSelectedDistrict('');
    setSelectedCropName('감자');
    setHomeData(null);
    setApiReport(null);
    setAnalysisState({ kind: 'IDLE' });
    setLastAnalysisRequest(null);
    setPendingCropRegistration(null);
    setIsFieldRegistrationFlow(false);
    if (pollTimerRef.current !== null) window.clearTimeout(pollTimerRef.current);
    setPosts([]);
    setMyFields([]);
    setHomeLoadError(null);
    setFieldLoadError(null);
    setCommunityLoadError(null);
    setCommunityComposeError(null);
    previewModeRef.current = false;
    setIsPreviewMode(false);
    setIsNewUser(true);
    setActiveTab('home');

    // 3. Navigate back to Landing screen
    setViewStep('landing');
  };

  const openPreviewDashboard = () => {
    previewModeRef.current = true;
    setIsPreviewMode(true);
    setActiveTab('home');
    setIsAIChatOpen(false);
    setUserName('사용자님');
    setUserEmail('미인증 계정');
    setSelectedProvince(MOCK_REGION_REPORT.region.sidoName);
    setSelectedDistrict(MOCK_REGION_REPORT.region.sigunguName);
    setSelectedCropName('감자');
    setApiReport(MOCK_REGION_REPORT); // TEMP: mock completed analysis so crop registration doesn't bounce to region-select
    setAnalysisState({ kind: 'COMPLETED', report: MOCK_REGION_REPORT });
    setLastAnalysisRequest(null);
    setPendingCropRegistration(null);
    setIsFieldRegistrationFlow(false);
    setHomeData(null);
    setMyFields(MOCK_FIELDS); // TEMP: show mock fields in preview mode for MyFieldListView design work
    setPosts([]);
    setHomeLoadError(null);
    setFieldLoadError(null);
    setCommunityLoadError(null);
    setCommunityComposeError(null);
    setIsNewUser(false);
    setViewStep('dashboard');
  };

  const returnToMyField = () => {
    setIsFieldRegistrationFlow(false);
    setPendingCropRegistration(null);
    setActiveTab('myfield');
    setViewStep('myfield');
  };

  const handleSelectField = (field: FieldProfile) => {
    setSelectedField(field);
    setViewStep('field_dashboard');
  };

  const openCropRegistrationFromMyField = () => {
    setActiveTab('myfield');
    if (!canOpenReport(analysisState)) {
      setIsFieldRegistrationFlow(false);
      setPendingCropRegistration(null);
      setViewStep('explore');
      return;
    }
    setIsFieldRegistrationFlow(true);
    setViewStep('condition');
  };

  const returnToCropCondition = () => {
    if (!isFieldRegistrationFlow || !pendingCropRegistration || !apiReport || !canOpenReport(analysisState)) {
      returnToMyField();
      return;
    }
    setViewStep('condition');
  };

  const openExploreFromCropRegistration = () => {
    setIsFieldRegistrationFlow(false);
    setPendingCropRegistration(null);
    setExploreMode('change');
    setViewStep('explore');
  };

  const safeSetViewStep = (targetStep: ExtendedViewStep) => {
    const cropRegistrationStep = targetStep === 'condition' || targetStep === 'recommended_crops' || targetStep === 'crop_suitability_report';
    if (cropRegistrationStep) {
      returnToMyField();
      return;
    }
    const reportStep = targetStep === 'report_summary' || targetStep === 'report_risks' || targetStep === 'report_tips';
    if (reportStep && !canOpenReport(analysisState)) {
      if (analysisState.kind === 'ERROR' || analysisState.kind === 'UNAUTHORIZED') {
        setViewStep('analyzing');
      } else {
        setViewStep('explore');
      }
      return;
    }
    setViewStep(targetStep);
  };

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const targetView = params.get('view');
    let isCurrent = true;

    if (token) {
      localStorage.setItem('jwtToken', token);
      window.history.replaceState({}, document.title, window.location.pathname);
    }

    const clearInvalidSession = (error: ApiError) => {
      if (previewModeRef.current) return;
      localStorage.removeItem('jwtToken');
      localStorage.removeItem('token');
      if (!isCurrent) return;
      setHomeLoadError(error.message);
      setViewStep('landing');
    };

    const initializeAuthenticatedSession = async () => {
      if (!checkHasToken()) {
        if (isCurrent) setViewStep('landing');
        return;
      }
      try {
        const resData = await ApiService.getHome();
        if (!isCurrent || previewModeRef.current) return;
        setHomeData(resData);
        setHomeLoadError(null);
        if (resData.user?.displayName) {
          setUserName(resData.user.displayName);
          localStorage.setItem('farmflate_user_name', resData.user.displayName);
        }
        if (resData.user?.email) {
          setUserEmail(resData.user.email);
          localStorage.setItem('farmflate_user_email', resData.user.email);
        }
        if (resData.latestRegionAnalysis?.regionName) {
          const parts = resData.latestRegionAnalysis.regionName.trim().split(' ');
          if (parts.length >= 2) {
            setSelectedProvince(parts[0]);
            setSelectedDistrict(parts.slice(1).join(' '));
          }
        }
        if (resData.latestRegionAnalysis?.analysisId) {
          void ApiService.getRegionReport(resData.latestRegionAnalysis.analysisId, 'COMPLETED')
            .then(report => {
              if (isCurrent) {
                setApiReport(report);
                setAnalysisState(stateFromAnalysisStatus({ analysisId: resData.latestRegionAnalysis!.analysisId, status: report.status || 'COMPLETED' }, report));
              }
            })
            .catch(e => console.warn('Failed to pre-fetch latest report on session init:', e));
        }

        setIsNewUser(!resData.latestRegionAnalysis);
        setViewStep(targetView === 'explore' ? 'explore' : targetView === 'landing' ? 'landing' : 'dashboard');

        void ApiService.getCommunityPosts()
          .then(data => { if (isCurrent && !previewModeRef.current) { setPosts(normalizeCommunityPosts(data)); setCommunityLoadError(null); } })
          .catch(error => { if (isCurrent && !previewModeRef.current) setCommunityLoadError(error instanceof Error ? error.message : '게시글을 불러오지 못했습니다.'); });
        void ApiService.getFields()
          .then(data => { if (isCurrent && !previewModeRef.current) { setMyFields(data); setFieldLoadError(null); } })
          .catch(error => { if (isCurrent && !previewModeRef.current) setFieldLoadError(error instanceof Error ? error.message : '밭 정보를 불러오지 못했습니다.'); });
      } catch (error) {
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          clearInvalidSession(error);
          return;
        }
        if (isCurrent && !previewModeRef.current) {
          setHomeLoadError(error instanceof Error ? error.message : '계정 정보를 확인하지 못했습니다.');
          setViewStep('landing');
        }
      }
    };

    void initializeAuthenticatedSession();

    return () => {
      isCurrent = false;
      if (pollTimerRef.current !== null) window.clearTimeout(pollTimerRef.current);
    };
  }, []);

  /* Refreshes the home summary (weather, today's action, latest analysis) in the
     background without touching viewStep/routing. Used by the daily 6am refresh
     below; the initial session bootstrap above has its own richer version. */
  const refreshHomeReport = async () => {
    if (!checkHasToken() || previewModeRef.current) return;
    try {
      const resData = await ApiService.getHome();
      if (previewModeRef.current) return;
      setHomeData(resData);
      setHomeLoadError(null);
      if (resData.user?.displayName) {
        setUserName(resData.user.displayName);
        localStorage.setItem('farmflate_user_name', resData.user.displayName);
      }
      if (resData.user?.email) {
        setUserEmail(resData.user.email);
        localStorage.setItem('farmflate_user_email', resData.user.email);
      }
      if (resData.latestRegionAnalysis?.analysisId) {
        const report = await ApiService.getRegionReport(resData.latestRegionAnalysis.analysisId, 'COMPLETED');
        if (previewModeRef.current) return;
        setApiReport(report);
        setAnalysisState(stateFromAnalysisStatus({ analysisId: resData.latestRegionAnalysis.analysisId, status: report.status || 'COMPLETED' }, report));
      }
      setIsNewUser(!resData.latestRegionAnalysis);
    } catch (error) {
      console.warn('Failed to refresh home report:', error);
    }
  };

  // Bumped once per day at 6am; passed as MainDashboardView's `key` so its
  // local "today's tasks" completion state resets for the new day.
  const [dailyKey, setDailyKey] = useState(() => new Date().toDateString());

  useDailyRefresh(() => {
    void refreshHomeReport();
    setDailyKey(new Date().toDateString());
  }, 6);

  const setAnalysisFailure = (error: unknown, pendingAction: 'ANALYSIS' | 'FIELD' = 'ANALYSIS') => {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      setAnalysisState({ kind: 'UNAUTHORIZED', message: error.message, pendingAction });
    } else if (error instanceof ApiError) {
      setAnalysisState({ kind: 'ERROR', message: error.message, code: error.code, retryable: error.retryable, pendingAction });
    } else {
      setAnalysisState({ kind: 'ERROR', message: '분석 결과를 확인하지 못했습니다.', retryable: true, pendingAction });
    }
    setViewStep('analyzing');
  };

  const [reportReturnStep, setReportReturnStep] = useState<ExtendedViewStep>('dashboard');

  const handleOpenConfirmedReport = async (analysisId?: string, sourceStep: ExtendedViewStep = 'dashboard') => {
    const targetId = analysisId || homeData?.latestRegionAnalysis?.analysisId || apiReport?.analysisId;
    if (!targetId) {
      safeSetViewStep('explore');
      return;
    }
    setReportReturnStep(sourceStep);
    setReportFlowSource('view');
    const previousAnalysisState = analysisState;
    try {
      setAnalysisState({ kind: 'SUBMITTING' });
      setViewStep('analyzing');
      const report = await ApiService.getRegionReport(targetId, 'COMPLETED');
      const nextState = stateFromAnalysisStatus({ analysisId: targetId, status: report.status || 'COMPLETED' }, report);
      setApiReport(report);
      setAnalysisState(nextState);
      setSelectedProvince(report.region.sidoName);
      setSelectedDistrict(report.region.sigunguName);
      setViewStep('report_summary');
    } catch (err) {
      console.warn('Failed to load confirmed report:', err);
      // Restore whatever analysis state existed before this attempt so a transient
      // failure here doesn't leave analysisState stuck at SUBMITTING (which would
      // otherwise block canOpenReport() from ever passing again) or wrongly
      // invalidate an already-valid, previously-loaded report.
      setAnalysisState(previousAnalysisState);
      if (sourceStep !== 'explore') {
        safeSetViewStep(sourceStep);
      } else {
        safeSetViewStep('explore');
      }
    }
  };

  const completeAnalysis = async (analysisId: string, status: 'COMPLETED' | 'PARTIAL') => {
    const report = await ApiService.getRegionReport(analysisId, status);
    const nextState = stateFromAnalysisStatus({ analysisId, status }, report);
    if (!canOpenReport(nextState)) throw new ApiError(200, 'MALFORMED_REPORT', '검증 가능한 리포트를 받지 못했습니다.');
    setApiReport(report);
    setAnalysisState(nextState);
    setSelectedProvince(report.region.sidoName);
    setSelectedDistrict(report.region.sigunguName);
    localStorage.setItem('farmflate_province', report.region.sidoName);
    localStorage.setItem('farmflate_district', report.region.sigunguName);
    setIsNewUser(false);
    localStorage.setItem('farmflate_is_new_user', 'false');

    // Refresh Home summary from backend DB so homeData, home weather, and latestRegionAnalysis update everywhere in real-time
    try {
      const freshHome = await ApiService.getHome();
      setHomeData(freshHome);
    } catch (e) {
      console.warn('Failed to refresh home summary:', e);
    }

    setViewStep('report_summary');
  };

  const pollAnalysis = async (analysisId: string, attempt = 0): Promise<void> => {
    try {
      const status = await ApiService.getAnalysisStatus(analysisId);
      const normalized = status.status.toUpperCase();
      if (normalized === 'COMPLETED' || normalized === 'PARTIAL') {
        await completeAnalysis(analysisId, normalized);
        return;
      }
      if (normalized === 'FAILED') {
        setAnalysisState(stateFromAnalysisStatus(status));
        setViewStep('analyzing');
        return;
      }
      if (attempt >= 60) {
        setAnalysisState({ kind: 'ERROR', message: '분석 완료 여부를 오래 확인하지 못했습니다.', code: 'ANALYSIS_TIMEOUT', retryable: true });
        setViewStep('analyzing');
        return;
      }
      setAnalysisState(stateFromAnalysisStatus(status));
      pollTimerRef.current = window.setTimeout(() => { void pollAnalysis(analysisId, attempt + 1); }, 900);
    } catch (error) {
      setAnalysisFailure(error);
    }
  };

  /* Region analysis completes only when the backend returns a validated terminal report. */
  const handleStartAnalysis = async (input: Omit<RegionAnalysisRequest, 'idempotencyKey'>) => {
    if (pollTimerRef.current !== null) window.clearTimeout(pollTimerRef.current);
    setReportFlowSource('onboarding');
    const request: RegionAnalysisRequest = { ...input, idempotencyKey: crypto.randomUUID() };
    setLastAnalysisRequest(request);
    setSelectedProvince(request.sidoName);
    setSelectedDistrict(request.sigunguName);
    localStorage.setItem('farmflate_province', request.sidoName);
    localStorage.setItem('farmflate_district', request.sigunguName);
    setApiReport(null);
    setPendingCropRegistration(null);
    setIsFieldRegistrationFlow(false);
    setAnalysisState({ kind: 'SUBMITTING' });
    setViewStep('analyzing');
    try {
      const status = await ApiService.createRegionAnalysis(request);
      const normalized = status.status.toUpperCase();
      if (normalized === 'COMPLETED' || normalized === 'PARTIAL') {
        await completeAnalysis(status.analysisId, normalized);
        return;
      }
      if (normalized === 'FAILED') {
        setAnalysisState(stateFromAnalysisStatus(status));
        return;
      }
      await pollAnalysis(status.analysisId);
    } catch (error) {
      setAnalysisFailure(error);
    }
  };

  const retryAnalysis = () => {
    if (lastAnalysisRequest) {
      const { idempotencyKey: _idempotencyKey, ...request } = lastAnalysisRequest;
      void handleStartAnalysis({ ...request, forceRefresh: true });
    } else {
      setViewStep('explore');
    }
  };

  /* Crop registration requires a verified server report. */
  const handleStartCropConditionAnalysis = (input: CropRegistrationInput) => {
    if (!isFieldRegistrationFlow || !apiReport || !canOpenReport(analysisState)) {
      returnToMyField();
      return;
    }
    setSelectedCropName(input.cropName);
    setPendingCropRegistration(input);
    setViewStep('crop_suitability_report');
  };

  const handleAddField = async () => {
    if (!isFieldRegistrationFlow || !apiReport || !pendingCropRegistration) {
      returnToMyField();
      return;
    }
    // Guard against duplicate registrations from rapid repeat clicks on
    // "농작물 등록하기" — only the first click while a submission is in
    // flight actually calls the backend; the rest are no-ops. Checked via a
    // ref (not state) because it must take effect synchronously, before any
    // re-render could disable the button.
    if (isAddingFieldRef.current) return;
    isAddingFieldRef.current = true;
    try {
      const crop = apiReport.recommendedCrops.find(item => item.cropName === pendingCropRegistration.cropName);
      const field = await ApiService.createField({
        fieldName: pendingCropRegistration.fieldName,
        cropCode: crop?.cropCode ?? undefined,
        cropName: pendingCropRegistration.cropName,
        cultivationMethod: pendingCropRegistration.farmType,
        cultivationStartDate: pendingCropRegistration.startDate,
        stage: pendingCropRegistration.stage,
        regionAnalysisId: apiReport.analysisId
      });
      setMyFields(previous => [field, ...previous.filter(item => item.id !== field.id)]);
      setPendingCropRegistration(null);
      setIsFieldRegistrationFlow(false);
      setActiveTab('myfield');
      setViewStep('myfield');
    } catch (error) {
      setAnalysisFailure(error, 'FIELD');
    } finally {
      isAddingFieldRef.current = false;
    }
  };

  /* Tab Navigation Handler */
  const handleTabChange = (tab: TabState) => {
    setActiveTab(tab);
    if (tab === 'home') safeSetViewStep('dashboard');
    if (tab === 'myfield') safeSetViewStep('myfield');
    if (tab === 'community') safeSetViewStep('community');
    if (tab === 'settings') safeSetViewStep('mypage');
  };

  /* Community Handlers */
  const handleToggleLike = async (postId: string) => {
    try {
      await ApiService.likeCommunityPost(postId);
      const postsFromServer = await ApiService.getCommunityPosts();
      setPosts(normalizeCommunityPosts(postsFromServer));
    } catch (err) {
      setCommunityLoadError('좋아요 처리에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  const handleToggleSave = (postId: string) => {
    setPosts(prev => prev.map(p => p.id === postId ? { ...p, isSaved: !p.isSaved } : p));
  };

  const handleAddComment = async (postId: string, commentText: string) => {
    try {
      await ApiService.addCommunityComment(postId, {
        content: commentText
      });
      const postsFromServer = await ApiService.getCommunityPosts();
      setPosts(normalizeCommunityPosts(postsFromServer));
    } catch (err) {
      setCommunityLoadError('댓글 등록에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  const handleCreatePost = async (title: string, content: string, category?: string, locationTag?: string, imageUrl?: string) => {
    setCommunityComposeError(null);
    try {
      await ApiService.createCommunityPost({
        title,
        content,
        category: category || '농가 노하우',
        tagLocation: locationTag || `${selectedProvince} ${selectedDistrict}`,
        imageUrl: imageUrl || ''
      });
      const postsFromServer = await ApiService.getCommunityPosts();
      setPosts(normalizeCommunityPosts(postsFromServer));
      setCommunityLoadError(null);
      safeSetViewStep('community');
      setActiveTab('community');
    } catch (err) {
      setCommunityComposeError(err instanceof Error ? err.message : '게시글을 등록하지 못했습니다.');
    }
  };

  return (
    <div className="mobile-wrapper min-h-screen bg-white" data-preview-mode={isPreviewMode ? 'true' : undefined}>
      {/* 0. Splash Screen */}
      {viewStep === 'splash' && (
        <SplashView onComplete={() => {
          if (!checkHasToken()) {
            safeSetViewStep('landing');
          }
        }} />
      )}

      {/* 1. Landing Screen (Kakao OAuth Login) */}
      {viewStep === 'landing' && (
        <LandingView errorMessage={homeLoadError} onOpenPreview={openPreviewDashboard} />
      )}

      {/* 2. Region Search Screen */}
      {viewStep === 'explore' && (
        <RegionExploreView
          onBack={() => safeSetViewStep('dashboard')}
          onStartAnalysis={handleStartAnalysis}
          mode={exploreMode}
        />
      )}

      {/* 3. Crop Condition & Selection Screen (농작물/야채 등록 및 선택) */}
      {viewStep === 'condition' && (
        <CropConditionInputView
          onBack={returnToMyField}
          onStartAnalysis={handleStartCropConditionAnalysis}
          onOpenExplore={openExploreFromCropRegistration}
          selectedRegionName={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : (selectedProvince && selectedDistrict ? `${selectedProvince} ${selectedDistrict}` : '지역 분석 전')}
        />
      )}

      {/* 4. Animated Analysis Loading Screen */}
      {viewStep === 'analyzing' && (
        <AnalyzingView
          regionName={selectedProvince && selectedDistrict ? `${selectedProvince} ${selectedDistrict}` : '선택한 지역'}
          cropName={selectedCropName}
          analysisType={isFieldRegistrationFlow || pendingCropRegistration ? 'crop' : 'region'}
          fieldLabel={pendingCropRegistration?.fieldName}
          state={analysisState}
          onRetry={() => {
            if ((analysisState.kind === 'ERROR' || analysisState.kind === 'UNAUTHORIZED') && analysisState.pendingAction === 'FIELD') {
              void handleAddField();
              return;
            }
            retryAnalysis();
          }}
          onBack={() => {
            setAnalysisState({ kind: 'IDLE' });
            if ((analysisState.kind === 'ERROR' || analysisState.kind === 'UNAUTHORIZED') && analysisState.pendingAction === 'FIELD') {
              returnToMyField();
            } else {
              safeSetViewStep('explore');
            }
          }}
          onLogin={() => safeSetViewStep('landing')}
        />
      )}

      {/* 5. Region Report Summary Screen */}
      {viewStep === 'report_summary' && (
        <RegionReportSummaryView
          regionName={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : `${selectedProvince} ${selectedDistrict}`}
          report={apiReport}
          onBack={() => safeSetViewStep(reportReturnStep)}
          onNext={() => safeSetViewStep('report_risks')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
        />
      )}

      {/* 6. Region Risks Screen */}
      {viewStep === 'report_risks' && (
        <RegionRisksView
          report={apiReport}
          onBack={() => safeSetViewStep('report_summary')}
          onNext={() => { setTipsReturnStep('report_risks'); safeSetViewStep('report_tips'); }}
        />
      )}

      {/* 7. Region Tips Screen */}
      {viewStep === 'report_tips' && (
        <RegionTipsView
          districtName={apiReport?.region?.sigunguName || selectedDistrict}
          report={apiReport}
          onBack={() => setViewStep(tipsReturnStep)}
          variant={tipsReturnStep === 'crop_suitability_report' ? 'cropRegister' : (reportFlowSource === 'view' ? 'view' : 'default')}
          onRegisterCrop={() => {
            setIsFieldRegistrationFlow(true);
            setViewStep('condition');
          }}
          onSave={() => {
            setIsNewUser(false);
            localStorage.setItem('farmflate_is_new_user', 'false');
            handleTabChange('home');
          }}
          onConfirm={() => handleTabChange('home')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
        />
      )}

      {/* 8. Recommended Crops Screen */}
      {viewStep === 'recommended_crops' && (
        <RecommendedCropsView
          districtName={apiReport?.region?.sigunguName || selectedDistrict}
          report={apiReport}
          onBack={() => safeSetViewStep('report_tips')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          onSelectCrop={returnToMyField}
        />
      )}

      {/* 9. Crop Suitability Report Screen (농작물 적합도 리포트) */}
      {viewStep === 'crop_suitability_report' && (
        <CropSuitabilityReportView
          fieldName={pendingCropRegistration?.fieldName}
          cropName={selectedCropName}
          report={apiReport}
          onBack={returnToCropCondition}
          onRegisterCrop={isFieldRegistrationFlow && pendingCropRegistration ? handleAddField : returnToMyField}
          onOpenTips={() => { setTipsReturnStep('crop_suitability_report'); safeSetViewStep('report_tips'); }}
        />
      )}

      {/* 10. Main Dashboard Screen */}
      {viewStep === 'dashboard' && (
        <MainDashboardView
          key={dailyKey}
          userName={userName}
          analyzedRegion={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : undefined}
          homeData={homeData}
          loadError={homeLoadError}
          onGoToExplore={() => { setExploreMode('analyze'); safeSetViewStep('explore'); }}
          onOpenReport={() => { void handleOpenConfirmedReport(undefined, 'dashboard'); }}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          activeTab={activeTab}
          onTabChange={handleTabChange}
          isNewUser={isNewUser}
        />
      )}

      {/* 11. My Field List Screen */}
      {viewStep === 'myfield' && (
        <MyFieldListView
          fields={myFields}
          loadError={fieldLoadError}
          onAddField={openCropRegistrationFromMyField}
          onSelectField={handleSelectField}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          activeTab={activeTab}
          onTabChange={handleTabChange}
        />
      )}

      {/* 11b. Field Detail Dashboard Screen */}
      {viewStep === 'field_dashboard' && selectedField && (
        <FieldDashboardView
          field={selectedField}
          onBack={returnToMyField}
          onOpenAIChat={() => setIsAIChatOpen(true)}
        />
      )}

      {/* 12. Community List Screen */}
      {viewStep === 'community' && (
        <CommunityListView
          posts={posts}
          loadError={communityLoadError}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          onOpenWrite={() => safeSetViewStep('community_create')}
          activeTab={activeTab}
          onTabChange={handleTabChange}
          onToggleLike={handleToggleLike}
          onToggleSave={handleToggleSave}
          onAddComment={handleAddComment}
        />
      )}

      {/* 13. Community Create Screen */}
      {viewStep === 'community_create' && (
        <CommunityCreatePostView
          userRegion={selectedProvince && selectedDistrict ? `${selectedProvince} ${selectedDistrict}` : '지역 정보 없음'}
          errorMessage={communityComposeError}
          onCancel={() => { setCommunityComposeError(null); safeSetViewStep('community'); }}
          onSubmitPost={handleCreatePost}
        />
      )}

      {/* 14. My Page (Settings) Screen */}
      {viewStep === 'mypage' && (
        <MyPageView
          userName={userName}
          userEmail={userEmail}
          userRegion={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : (selectedProvince && selectedDistrict ? `${selectedProvince} ${selectedDistrict}` : '지역 정보 없음')}
          posts={posts}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          activeTab={activeTab}
          onTabChange={handleTabChange}
          onGoToExplore={() => { setExploreMode('analyze'); safeSetViewStep('explore'); }}
          onUpdateUserName={setUserName}
          onLogout={handleLogout}
          onToggleLike={handleToggleLike}
          onToggleSave={handleToggleSave}
          onAddComment={handleAddComment}
        />
      )}

      {/* 15. Global Farmflate AI Bottom Sheet Modal */}
      <AIChatModal
        isOpen={isAIChatOpen}
        onClose={() => setIsAIChatOpen(false)}
        regionAnalysisId={apiReport?.analysisId ?? null}
      />
    </div>
  );
}

export default App;
