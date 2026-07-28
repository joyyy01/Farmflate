import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom';
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
import type { FieldProfile, FieldSuitabilityPreview, HomeData, RegionAnalysisRequest, RegionReport } from './services/api';
import { canOpenReport, needsFreshCropAnalysis, stateFromAnalysisStatus, type AnalysisState, type FieldPreviewState } from './services/reportLifecycle';
import { AIChatModal } from './components/farmflate/AIChatModal';
import { useDailyRefresh } from './hooks/useDailyRefresh';
import type { NavigationFlow } from './types/navigation';
import type { ChatRoute } from './types/chat';
import type { VisibleDataRef } from './types/chat';
import { buildRegionVisibleData } from './services/visibleDataContext';

type ExtendedViewStep = ViewStep | 'splash';

/* URL for every screen except field_dashboard, which carries a :fieldId segment
   and is navigated to directly (see handleSelectField) rather than through this map. */
const VIEW_STEP_PATH: Record<Exclude<ExtendedViewStep, 'field_dashboard'>, string> = {
  splash: '/',
  landing: '/landing',
  explore: '/explore',
  analyzing: '/analyzing',
  report_summary: '/report/summary',
  report_risks: '/report/risks',
  report_tips: '/report/tips',
  recommended_crops: '/report/recommended-crops',
  crop_suitability_report: '/crop/suitability',
  condition: '/crop/condition',
  dashboard: '/dashboard',
  myfield: '/myfield',
  community: '/community',
  community_create: '/community/write',
  mypage: '/mypage'
};

const PATH_TO_VIEW_STEP: Record<string, ExtendedViewStep> = Object.fromEntries(
  Object.entries(VIEW_STEP_PATH).map(([step, path]) => [path, step as ExtendedViewStep])
);

const pathToViewStep = (pathname: string): ExtendedViewStep => {
  if (pathname.startsWith('/field/')) return 'field_dashboard';
  return PATH_TO_VIEW_STEP[pathname] ?? 'splash';
};

const STEP_CODE_TO_UI_INDEX: Record<string, number> = {
  REGION: 0,
  RECENT_WEATHER: 1,
  FORECAST: 1,
  SOIL: 2,
  CROP: 3,
  REPORT: 4
};

const maxUiStepFromCodes = (codes: string[]): number => {
  let max = -1;
  for (const code of codes) {
    const idx = STEP_CODE_TO_UI_INDEX[code.toUpperCase()];
    if (idx !== undefined && idx > max) max = idx;
  }
  return max;
};

const aiChatRouteFor = (viewStep: ExtendedViewStep): ChatRoute => {
  switch (viewStep) {
    case 'field_dashboard':
      return 'field_dashboard';
    case 'community':
    case 'community_create':
      return 'community';
    case 'mypage':
      return 'mypage';
    case 'report_summary':
    case 'report_risks':
    case 'report_tips':
    case 'recommended_crops':
    case 'crop_suitability_report':
      return 'region_report';
    default:
      return 'home';
  }
};

const displayNameOf = (author: unknown): string => {
  if (typeof author === 'string') return author;
  if (author && typeof author === 'object' && typeof (author as Record<string, unknown>).displayName === 'string') {
    return (author as Record<string, unknown>).displayName as string;
  }
  return '사용자';
};

const normalizeCommunityPosts = (data: unknown): CommunityPost[] => {
  if (!Array.isArray(data)) throw new ApiError(200, 'MALFORMED_COMMUNITY_POSTS', '게시글 목록 응답이 올바르지 않습니다.', data, false);
  return data.map((item): CommunityPost => {
    if (!item || typeof item !== 'object') throw new ApiError(200, 'MALFORMED_COMMUNITY_POSTS', '게시글 항목이 올바르지 않습니다.', item, false);
    const post = item as Record<string, unknown>;
    const id = typeof post.id === 'string' || typeof post.id === 'number' ? String(post.id) : '';
    const title = typeof post.title === 'string' ? post.title : '';
    if (!id || !title || typeof post.content !== 'string' || typeof post.likeCount !== 'number' || typeof post.commentCount !== 'number') {
      throw new ApiError(200, 'MALFORMED_COMMUNITY_POSTS', '게시글 항목이 올바르지 않습니다.', item, false);
    }
    const attachments: CommunityPost['attachments'] = Array.isArray(post.attachments)
      ? (post.attachments as Record<string, unknown>[]).map((a): CommunityPost['attachments'][number] => ({
          id: String(a.id ?? ''),
          type: (a.type === 'IMAGE' || a.type === 'FILE' || a.type === 'LINK') ? a.type : 'FILE',
          name: typeof a.name === 'string' ? a.name : '첨부',
          contentType: typeof a.contentType === 'string' ? a.contentType : null,
          sizeBytes: typeof a.sizeBytes === 'number' ? a.sizeBytes : null,
          url: typeof a.url === 'string' ? a.url : '',
          order: typeof a.order === 'number' ? a.order : 0
        }))
      : [];
    const comments = Array.isArray(post.comments)
      ? (post.comments as Record<string, unknown>[]).map((c) => ({
          id: String(c.id ?? ''),
          author: displayNameOf(c.author),
          content: typeof c.content === 'string' ? c.content : '',
          timeAgo: typeof c.timeAgo === 'string' ? c.timeAgo : '시간 정보 없음'
        }))
      : [];
    return {
      id,
      regionLabel: typeof post.regionLabel === 'string' ? post.regionLabel : '지역 정보 없음',
      title,
      content: post.content,
      author: displayNameOf(post.author),
      profileType: 'DEFAULT',
      timeAgo: typeof post.timeAgo === 'string' ? post.timeAgo : '시간 정보 없음',
      commentCount: post.commentCount,
      likeCount: post.likeCount,
      isLiked: typeof post.likedByMe === 'boolean' ? post.likedByMe : false,
      isSaved: typeof post.savedByMe === 'boolean' ? post.savedByMe : false,
      attachments,
      comments
    };
  });
};

export function App() {
  const navigate = useNavigate();
  const location = useLocation();
  // App is rendered as the element of the outer "/*" route (see main.tsx), so
  // useParams() here would resolve against that outer match (no named params)
  // rather than the inner "/field/:fieldId" route App defines in its own JSX
  // below. Parse the id straight from the pathname instead.
  const fieldIdFromPath = useMemo(() => location.pathname.match(/^\/field\/([^/]+)/)?.[1], [location.pathname]);
  const viewStep = useMemo(() => pathToViewStep(location.pathname), [location.pathname]);
  const setViewStep = (step: ExtendedViewStep, options?: { replace?: boolean }) => {
    navigate(VIEW_STEP_PATH[step as Exclude<ExtendedViewStep, 'field_dashboard'>] ?? '/', { replace: options?.replace });
  };
  const [activeTab, setActiveTab] = useState<TabState>('home');
  const [isAIChatOpen, setIsAIChatOpen] = useState(false);

  /* Explore Screen Context (Fresh Region Analysis vs Changing an Existing Selection) */
  const [exploreMode, setExploreMode] = useState<'analyze' | 'change'>('analyze');
  /* Where the explore screen's own back button (and, on completion, the
     resulting report chain's back button) should return to: MainDashboardView
     and MyPageView both open a fresh 'analyze' explore session but expect
     back navigation to land on whichever screen opened it. */
  const [exploreReturnStep, setExploreReturnStep] = useState<'dashboard' | 'mypage'>('dashboard');
  /* A field's region is just "which region is this crop in" — a lightweight
     pick, unlike the user's primary/representative region (set from Home or
     My Page) which drives the full analysis + report experience. Picking a
     region here still needs a backing region analysis (suitability scoring
     reads from it) but resolves it silently, with no visible analyzing/report
     screens, then returns straight to the registration form. */
  const [isResolvingFieldRegion, setIsResolvingFieldRegion] = useState(false);
  const [fieldRegionError, setFieldRegionError] = useState<string | null>(null);

  /* Which multi-step flow (if any) is currently in progress, and where its
     exit points should return to. Replaces the previous separate
     tipsReturnStep/isFieldRegistrationFlow booleans so the field-registration
     and tips-loop navigation is defined in one place. */
  const [navigationFlow, setNavigationFlow] = useState<NavigationFlow>({ kind: 'NONE' });
  const isFieldRegistrationFlow =
    navigationFlow.kind === 'FIELD_REGISTRATION' || navigationFlow.kind === 'FIELD_REGISTRATION_TIPS';

  /* Whether the report_summary -> report_risks -> report_tips chain was entered
     fresh (onboarding) or by re-viewing an already-analyzed report from the dashboard */
  const [reportFlowSource, setReportFlowSource] = useState<'onboarding' | 'view'>('onboarding');

  /* Clean Unauthenticated Default States (No Hardcoded Private Names) */
  const [isNewUser, setIsNewUser] = useState<boolean>(() => {
    const cached = localStorage.getItem('farmflate_is_new_user');
    return cached !== null ? JSON.parse(cached) : true;
  });

  const [userName, setUserName] = useState<string>(() => {
    return localStorage.getItem('farmflate_user_name') || '사용자님';
  });

  const [userEmail, setUserEmail] = useState<string>(() => {
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
  /* Keeps the crop-registration form's typed values alive across a region-change
     round trip (which unmounts/remounts CropConditionInputView via the router). */
  const [cropRegistrationDraft, setCropRegistrationDraft] = useState<CropRegistrationInput | null>(null);
  const [fieldPreview, setFieldPreview] = useState<FieldSuitabilityPreview | null>(null);
  const [fieldPreviewState, setFieldPreviewState] = useState<FieldPreviewState>({ kind: 'IDLE' });
  const [homeData, setHomeData] = useState<HomeData | null>(null);
  const [myFields, setMyFields] = useState<FieldProfile[]>([]);
  const [selectedField, setSelectedField] = useState<FieldProfile | null>(null);
  const [fieldChatReportDate, setFieldChatReportDate] = useState<string | null>(null);
  const [fieldVisibleData, setFieldVisibleData] = useState<VisibleDataRef[]>([]);

  /* Landing directly on /field/:fieldId (a refresh, a shared link, browser
     back/forward) needs to resolve selectedField from the URL once the
     field list has loaded, since the field object itself is never encoded
     in the URL. */
  useEffect(() => {
    if (viewStep !== 'field_dashboard') return;
    if (selectedField && String(selectedField.id) === fieldIdFromPath) return;
    const match = myFields.find(field => String(field.id) === fieldIdFromPath);
    if (match) setSelectedField(match);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewStep, fieldIdFromPath, myFields]);
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [homeLoadError, setHomeLoadError] = useState<string | null>(null);
  const [fieldLoadError, setFieldLoadError] = useState<string | null>(null);
  const [communityLoadError, setCommunityLoadError] = useState<string | null>(null);
  const [communityComposeError, setCommunityComposeError] = useState<string | null>(null);
  const pollTimerRef = useRef<number | null>(null);
  const isAddingFieldRef = useRef(false);
  const lastDisplayedStepRef = useRef(0);
  const activeAnalysisRunRef = useRef(0);
  const cropStepTimerRef = useRef<number | null>(null);
  const cropStepRef = useRef(0);

  /* Full 100% Reliable Logout Reset Handler */
  const handleLogout = async () => {
    try {
      await ApiService.logout();
    } catch {
      // The local session still needs to close if the access cookie has already expired.
    }
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
    setNavigationFlow({ kind: 'NONE' });
    if (pollTimerRef.current !== null) window.clearTimeout(pollTimerRef.current);
    activeAnalysisRunRef.current += 1;
    setPosts([]);
    setMyFields([]);
    setHomeLoadError(null);
    setFieldLoadError(null);
    setCommunityLoadError(null);
    setCommunityComposeError(null);
    setIsNewUser(true);
    setActiveTab('home');

    // 3. Navigate back to Landing screen
    setViewStep('landing', { replace: true });
  };

  const returnToMyField = () => {
    setNavigationFlow({ kind: 'NONE' });
    setPendingCropRegistration(null);
    setCropRegistrationDraft(null);
    setActiveTab('myfield');
    setViewStep('myfield');
  };

  const handleSelectField = (field: FieldProfile) => {
    setSelectedField(field);
    setFieldChatReportDate(null);
    setFieldVisibleData([]);
    navigate(`/field/${field.id}`);
  };

  const handleFieldVisibleDataChange = useCallback((visibleData: VisibleDataRef[], reportDate: string | null) => {
    setFieldVisibleData(visibleData);
    setFieldChatReportDate(reportDate);
  }, []);

  const openCropRegistrationFromMyField = () => {
    setActiveTab('myfield');
    if (!canOpenReport(analysisState)) {
      setNavigationFlow({ kind: 'NONE' });
      setPendingCropRegistration(null);
      setViewStep('explore');
      return;
    }
    setNavigationFlow({ kind: 'FIELD_REGISTRATION', returnTo: 'myfield' });
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
    setFieldRegionError(null);
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
    const targetView = params.get('view');
    const bootPathname = window.location.pathname;
    let isCurrent = true;

    localStorage.removeItem('jwtToken');
    localStorage.removeItem('token');

    const clearInvalidSession = () => {
      localStorage.removeItem('jwtToken');
      localStorage.removeItem('token');
      if (!isCurrent) return;
      setHomeLoadError(null);
      setViewStep('landing', { replace: true });
    };

    const initializeAuthenticatedSession = async () => {
      try {
        const resData = await ApiService.getHome();
        if (!isCurrent) return;
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
        // A hard refresh/deep link on an authenticated screen (e.g. /myfield)
        // should stay put; only splash ('/') or an explicit ?view= param
        // decides where to land.
        if (targetView === 'explore') {
          setViewStep('explore', { replace: true });
        } else if (targetView === 'landing') {
          setViewStep('landing', { replace: true });
        } else if (bootPathname === '/' || bootPathname === VIEW_STEP_PATH.landing) {
          setViewStep('dashboard', { replace: true });
        }

        void ApiService.getCommunityPosts()
          .then(data => { if (isCurrent) { setPosts(normalizeCommunityPosts(data)); setCommunityLoadError(null); } })
          .catch(error => { if (isCurrent) setCommunityLoadError(error instanceof Error ? error.message : '게시글을 불러오지 못했습니다.'); });
        void ApiService.getFields()
          .then(data => { if (isCurrent) { setMyFields(data); setFieldLoadError(null); } })
          .catch(error => { if (isCurrent) setFieldLoadError(error instanceof Error ? error.message : '밭 정보를 불러오지 못했습니다.'); });
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          clearInvalidSession();
          return;
        }
        if (isCurrent) {
          if (error instanceof ApiError && error.status === 403) {
            setHomeLoadError('접근 권한이 없습니다.');
          } else {
            setHomeLoadError(error instanceof Error ? error.message : '홈 정보를 불러오지 못했습니다. 다시 시도해 주세요.');
          }
          if (bootPathname === '/' || bootPathname === VIEW_STEP_PATH.landing) {
            setViewStep('dashboard', { replace: true });
          }
        }
      }
    };

    void initializeAuthenticatedSession();

    return () => {
      isCurrent = false;
      if (pollTimerRef.current !== null) window.clearTimeout(pollTimerRef.current);
      activeAnalysisRunRef.current += 1;
    };
  }, []);

  /* Refreshes the home summary (weather, today's action, latest analysis) in the
     background without touching viewStep/routing. Used by the daily 6am refresh
     below; the initial session bootstrap above has its own richer version. */
  const refreshHomeReport = async () => {
    if (!homeData?.user?.email) return;
    try {
      const resData = await ApiService.getHome();
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

  const setAnalysisFailure = (error: unknown, pendingAction: 'REGION_ANALYSIS' | 'FIELD_PREVIEW' | 'FIELD_CREATE' = 'REGION_ANALYSIS') => {
    if (error instanceof ApiError && error.status === 401) {
      setAnalysisState({ kind: 'UNAUTHORIZED', message: error.message, pendingAction });
    } else if (error instanceof ApiError) {
      setAnalysisState({ kind: 'ERROR', message: error.message, code: error.code, retryable: error.retryable, pendingAction });
    } else {
      setAnalysisState({ kind: 'ERROR', message: '분석 결과를 확인하지 못했습니다.', retryable: true, pendingAction });
    }
    setViewStep('analyzing');
  };

  const setFieldPreviewFailure = (error: unknown) => {
    if (error instanceof ApiError && error.status === 401) {
      setFieldPreviewState({ kind: 'UNAUTHORIZED', message: error.message });
    } else if (error instanceof ApiError) {
      setFieldPreviewState({ kind: 'ERROR', message: error.message, code: error.code, retryable: error.retryable });
    } else {
      setFieldPreviewState({ kind: 'ERROR', message: '적합도 분석 결과를 확인하지 못했습니다.', retryable: true });
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
      setAnalysisState(previousAnalysisState);
      if (sourceStep !== 'explore') {
        safeSetViewStep(sourceStep);
      } else {
        safeSetViewStep('explore');
      }
    }
  };

  const completeAnalysis = async (
    analysisId: string,
    status: 'COMPLETED' | 'PARTIAL',
    terminalStepCodes?: string[],
    runId = activeAnalysisRunRef.current
  ) => {
    const report = await ApiService.getRegionReport(analysisId, status);
    if (runId !== activeAnalysisRunRef.current) return;
    const nextState = stateFromAnalysisStatus({ analysisId, status }, report);
    if (!canOpenReport(nextState)) throw new ApiError(200, 'MALFORMED_REPORT', '검증 가능한 리포트를 받지 못했습니다.');

    // Terminal catch-up: animate missing UI steps before navigating. Each step
    // is held slightly longer than one full spinner rotation (the CSS
    // .spinner-rotate animation is 0.8s) so a cached/instant analysis still
    // visibly completes at least one full spin instead of flashing past the
    // step before the rotation finishes.
    const terminalMax = terminalStepCodes ? maxUiStepFromCodes(terminalStepCodes) : 4;
    const lastShown = lastDisplayedStepRef.current;
    if (terminalMax > lastShown) {
      for (let step = lastShown + 1; step <= terminalMax; step++) {
        if (runId !== activeAnalysisRunRef.current) return;
        setAnalysisState({
          kind: 'POLLING',
          analysisId,
          currentStep: null,
          completedSteps: [],
          currentStepCode: null,
          completedStepCodes: Object.entries(STEP_CODE_TO_UI_INDEX)
            .filter(([, idx]) => idx < step)
            .map(([code]) => code)
        });
        lastDisplayedStepRef.current = step;
        await new Promise(resolve => setTimeout(resolve, 900));
      }
      await new Promise(resolve => setTimeout(resolve, 200));
    }
    if (runId !== activeAnalysisRunRef.current) return;

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

  const pollAnalysis = async (analysisId: string, attempt = 0, runId = activeAnalysisRunRef.current): Promise<void> => {
    if (runId !== activeAnalysisRunRef.current) return;
    try {
      const status = await ApiService.getAnalysisStatus(analysisId);
      if (runId !== activeAnalysisRunRef.current) return;
      const normalized = status.status.toUpperCase();
      if (normalized === 'COMPLETED' || normalized === 'PARTIAL') {
        await completeAnalysis(analysisId, normalized, status.completedStepCodes, runId);
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
      const pollingState = stateFromAnalysisStatus(status);
      setAnalysisState(pollingState);
      // Track the highest UI step shown so far for terminal catch-up
      if (pollingState.kind === 'POLLING') {
        const codes = pollingState.completedStepCodes ?? [];
        const currentCode = pollingState.currentStepCode;
        const allCodes = currentCode ? [...codes, currentCode] : codes;
        const maxStep = maxUiStepFromCodes(allCodes);
        if (maxStep > lastDisplayedStepRef.current) {
          lastDisplayedStepRef.current = maxStep;
        }
      }
      pollTimerRef.current = window.setTimeout(() => { void pollAnalysis(analysisId, attempt + 1, runId); }, 900);
    } catch (error) {
      if (runId === activeAnalysisRunRef.current) setAnalysisFailure(error);
    }
  };

  /* Region analysis completes only when the backend returns a validated terminal report. */
  const handleStartAnalysis = async (input: Omit<RegionAnalysisRequest, 'idempotencyKey'>) => {
    if (pollTimerRef.current !== null) window.clearTimeout(pollTimerRef.current);
    const runId = activeAnalysisRunRef.current + 1;
    activeAnalysisRunRef.current = runId;
    setReportFlowSource('onboarding');
    const request: RegionAnalysisRequest = { ...input, idempotencyKey: crypto.randomUUID(), purpose: 'PRIMARY' };
    setLastAnalysisRequest(request);
    setSelectedProvince(request.sidoName);
    setSelectedDistrict(request.sigunguName);
    localStorage.setItem('farmflate_province', request.sidoName);
    localStorage.setItem('farmflate_district', request.sigunguName);
    setApiReport(null);
    setPendingCropRegistration(null);
    setNavigationFlow({ kind: 'NONE' });
    lastDisplayedStepRef.current = 0;
    setAnalysisState({ kind: 'SUBMITTING' });
    setViewStep('analyzing');
    try {
      const status = await ApiService.createRegionAnalysis(request);
      const normalized = status.status.toUpperCase();
      if (normalized === 'COMPLETED' || normalized === 'PARTIAL') {
        await completeAnalysis(status.analysisId, normalized, status.completedStepCodes, runId);
        return;
      }
      if (normalized === 'FAILED') {
        setAnalysisState(stateFromAnalysisStatus(status));
        return;
      }
      await pollAnalysis(status.analysisId, 0, runId);
    } catch (error) {
      if (runId === activeAnalysisRunRef.current) setAnalysisFailure(error);
    }
  };

  /* Silent counterpart to handleStartAnalysis for the crop-registration
     "which region is this field in" pick: still resolves a backing region
     analysis (suitability scoring reads from it), but never shows the
     analyzing/report screens -- it stays on the explore screen with a small
     inline loading state, then returns straight to the registration form. */
  const submitFieldRegionChange = async (input: Omit<RegionAnalysisRequest, 'idempotencyKey'>): Promise<RegionReport | null> => {
    setFieldRegionError(null);
    setIsResolvingFieldRegion(true);
    const request: RegionAnalysisRequest = { ...input, idempotencyKey: crypto.randomUUID(), purpose: 'FIELD_LINKED' };
    try {
      const initial = await ApiService.createRegionAnalysis(request);
      let latest = initial;
      let normalized = latest.status.toUpperCase();
      let attempt = 0;
      while (normalized !== 'COMPLETED' && normalized !== 'PARTIAL' && normalized !== 'FAILED') {
        if (attempt >= 60) throw new Error('지역 정보를 확인하는 데 시간이 너무 오래 걸립니다. 다시 시도해 주세요.');
        await new Promise(resolve => setTimeout(resolve, 900));
        latest = await ApiService.getAnalysisStatus(latest.analysisId);
        normalized = latest.status.toUpperCase();
        attempt += 1;
      }
      if (normalized === 'FAILED') {
        throw new Error(latest.errorMessage ?? '지역 정보를 확인하지 못했습니다.');
      }
      const report = await ApiService.getRegionReport(latest.analysisId, normalized as 'COMPLETED' | 'PARTIAL');
      const nextState = stateFromAnalysisStatus({ ...latest, status: normalized }, report);
      if (!canOpenReport(nextState)) throw new Error('검증 가능한 지역 정보를 받지 못했습니다.');
      setApiReport(report);
      setAnalysisState(nextState);
      setSelectedProvince(report.region.sidoName);
      setSelectedDistrict(report.region.sigunguName);
      localStorage.setItem('farmflate_province', report.region.sidoName);
      localStorage.setItem('farmflate_district', report.region.sigunguName);
      setExploreMode('analyze');
      setViewStep('condition');
      return report;
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('token');
        setHomeLoadError('로그인이 만료되었습니다. 다시 로그인해 주세요.');
        setViewStep('landing', { replace: true });
        return null;
      }
      setFieldRegionError(error instanceof ApiError ? error.message : (error instanceof Error ? error.message : '지역 정보를 확인하지 못했습니다.'));
      return null;
    } finally {
      setIsResolvingFieldRegion(false);
    }
  };

  const refreshStaleCropRegistrationAnalysis = async () => {
    if (!apiReport || !pendingCropRegistration) {
      returnToCropCondition();
      return;
    }
    const report = await submitFieldRegionChange({
      ...apiReport.region,
      forceRefresh: true,
      purpose: 'FIELD_LINKED'
    });
    if (report) {
      await handleStartCropConditionAnalysis(pendingCropRegistration, report);
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
  const stopCropStepAdvance = () => {
    if (cropStepTimerRef.current !== null) {
      window.clearInterval(cropStepTimerRef.current);
      cropStepTimerRef.current = null;
    }
  };

  const handleStartCropConditionAnalysis = async (input: CropRegistrationInput, reportOverride?: RegionReport) => {
    const activeReport = reportOverride ?? apiReport;
    if (!isFieldRegistrationFlow || !activeReport || !canOpenReport(analysisState)) {
      returnToMyField();
      return;
    }
    setSelectedCropName(input.cropName);
    setPendingCropRegistration(input);
    setFieldPreview(null);

    // Start auto-advancing the displayed step while the request is in flight
    stopCropStepAdvance();
    cropStepRef.current = 0;
    setFieldPreviewState({ kind: 'SUBMITTING', step: 0 });
    setViewStep('analyzing');
    cropStepTimerRef.current = window.setInterval(() => {
      if (cropStepRef.current < 3) {
        cropStepRef.current += 1;
        setFieldPreviewState({ kind: 'SUBMITTING', step: cropStepRef.current });
      }
    }, 1400);

    try {
      const crop = activeReport.cropResults.find(item => item.cropName === input.cropName);
      if (!crop || !crop.cropCode) {
        stopCropStepAdvance();
        setFieldPreviewState({
          kind: 'ERROR',
          message: `선택한 작물(${input.cropName})의 분석 코드를 찾을 수 없습니다. 지역 분석 결과에서 계산 가능한 작물만 등록할 수 있습니다.`,
          retryable: false
        });
        return;
      }
      if (crop.calculable === false) {
        stopCropStepAdvance();
        const needsRefresh = needsFreshCropAnalysis(crop);
        setFieldPreviewState({
          kind: 'ERROR',
          code: needsRefresh ? 'STALE_CROP_ANALYSIS' : 'CROP_NOT_CALCULABLE',
          message: needsRefresh
            ? '이전 분석 규칙으로 저장된 결과입니다. 최신 공공데이터와 계산 규칙으로 다시 분석할게요.'
            : (crop.notCalculableReason ?? `선택한 작물(${input.cropName})은(는) 현재 지역 분석에서 적합도 계산이 불가능합니다.`),
          retryable: needsRefresh
        });
        return;
      }
      const preview = await ApiService.previewField({
        fieldName: input.fieldName,
        cropCode: crop.cropCode,
        cropName: input.cropName,
        cultivationMethod: input.farmType,
        cultivationStartDate: input.startDate,
        stage: input.stage,
        regionAnalysisId: activeReport.analysisId
      });
      stopCropStepAdvance();
      setFieldPreview(preview);

      // Continue animating from the currently displayed step through the final
      // step so every row shows the spinner before navigating to the report.
      const startStep = cropStepRef.current;
      for (let step = startStep; step <= 4; step++) {
        setFieldPreviewState({ kind: 'COMPLETING', completedStepIndex: step });
        await new Promise(resolve => setTimeout(resolve, 250));
      }
      setFieldPreviewState({ kind: 'COMPLETED' });
      setViewStep('crop_suitability_report');
    } catch (error) {
      stopCropStepAdvance();
      setFieldPreviewFailure(error);
    }
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
      const crop = apiReport.cropResults.find(item => item.cropName === pendingCropRegistration.cropName);
      if (!crop || !crop.cropCode) {
        setAnalysisState({ kind: 'ERROR', message: `작물(${pendingCropRegistration.cropName}) 코드를 찾을 수 없습니다.`, retryable: false, pendingAction: 'FIELD_CREATE' });
        setViewStep('analyzing');
        return;
      }
      const field = await ApiService.createField({
        fieldName: pendingCropRegistration.fieldName,
        cropCode: crop.cropCode,
        cropName: pendingCropRegistration.cropName,
        cultivationMethod: pendingCropRegistration.farmType,
        cultivationStartDate: pendingCropRegistration.startDate,
        stage: pendingCropRegistration.stage,
        regionAnalysisId: apiReport.analysisId
      });
      setMyFields(previous => [field, ...previous.filter(item => item.id !== field.id)]);
      setPendingCropRegistration(null);
      setCropRegistrationDraft(null);
      setNavigationFlow({ kind: 'NONE' });
      setActiveTab('myfield');
      setViewStep('myfield');
    } catch (error) {
      setAnalysisFailure(error, 'FIELD_CREATE');
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
    const previous = posts;
    const target = posts.find(p => p.id === postId);
    const targetLiked = !target?.isLiked;
    setPosts(current => current.map(p => p.id === postId
      ? { ...p, isLiked: targetLiked, likeCount: Math.max(0, p.likeCount + (targetLiked ? 1 : -1)) }
      : p));
    try {
      const result = targetLiked
        ? await ApiService.likeCommunityPost(postId)
        : await ApiService.unlikeCommunityPost(postId);
      setPosts(current => current.map(p => p.id === postId ? normalizeCommunityPosts([result])[0] : p));
    } catch (err) {
      setPosts(previous);
      setCommunityLoadError('좋아요 처리에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  const handleToggleSave = async (postId: string) => {
    try {
      const result = await ApiService.saveCommunityPost(postId);
      setPosts(prev => prev.map(p => p.id === postId ? { ...p, isSaved: result.isSaved } : p));
    } catch {
      setCommunityLoadError('저장 처리에 실패했습니다.');
    }
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

  const handleCreatePost = async (title: string, content: string, attachmentIds?: string[]) => {
    setCommunityComposeError(null);
    try {
      await ApiService.createCommunityPost({ title, content, attachmentIds });
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
    <div className="mobile-wrapper min-h-screen bg-white">
      <Routes>
        {/* 0. Splash Screen */}
        <Route path={VIEW_STEP_PATH.splash} element={
          <SplashView onComplete={() => undefined} />
        } />

        {/* 1. Landing Screen (Kakao OAuth Login) */}
        <Route path={VIEW_STEP_PATH.landing} element={
          <LandingView errorMessage={homeLoadError} />
        } />

        {/* 2. Region Search Screen */}
        <Route path={VIEW_STEP_PATH.explore} element={
          <RegionExploreView
            onBack={() => {
              if (exploreMode === 'change') {
                setFieldRegionError(null);
                setViewStep('condition');
                return;
              }
              safeSetViewStep(exploreReturnStep);
            }}
            onStartAnalysis={exploreMode === 'change' ? submitFieldRegionChange : handleStartAnalysis}
            mode={exploreMode}
            isSubmitting={exploreMode === 'change' ? isResolvingFieldRegion : undefined}
            submitError={exploreMode === 'change' ? fieldRegionError : undefined}
          />
        } />

        {/* 3. Crop Condition & Selection Screen (농작물/야채 등록 및 선택) */}
        <Route path={VIEW_STEP_PATH.condition} element={
          <CropConditionInputView
            onBack={returnToMyField}
            onStartAnalysis={handleStartCropConditionAnalysis}
            onOpenExplore={openExploreFromCropRegistration}
            selectedRegionName={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : (selectedProvince && selectedDistrict ? `${selectedProvince} ${selectedDistrict}` : '지역 분석 전')}
            draft={cropRegistrationDraft ?? undefined}
            onDraftChange={setCropRegistrationDraft}
          />
        } />

        {/* 4. Animated Analysis Loading Screen */}
        <Route path={VIEW_STEP_PATH.analyzing} element={
          <AnalyzingView
            regionName={selectedProvince && selectedDistrict ? `${selectedProvince} ${selectedDistrict}` : '선택한 지역'}
            cropName={selectedCropName}
            analysisType={isFieldRegistrationFlow || pendingCropRegistration ? 'crop' : 'region'}
            fieldLabel={pendingCropRegistration?.fieldName}
            state={isFieldRegistrationFlow || pendingCropRegistration ? fieldPreviewState : analysisState}
            onRetry={() => {
              if (fieldPreviewState.kind === 'ERROR' || fieldPreviewState.kind === 'UNAUTHORIZED') {
                if (fieldPreviewState.kind === 'ERROR' && fieldPreviewState.code === 'STALE_CROP_ANALYSIS') {
                  void refreshStaleCropRegistrationAnalysis();
                  return;
                }
                if (pendingCropRegistration) {
                  void handleStartCropConditionAnalysis(pendingCropRegistration);
                }
                return;
              }
              if ((analysisState.kind === 'ERROR' || analysisState.kind === 'UNAUTHORIZED') && analysisState.pendingAction === 'FIELD_CREATE') {
                void handleAddField();
                return;
              }
              retryAnalysis();
            }}
            onBack={() => {
              if (fieldPreviewState.kind === 'ERROR' || fieldPreviewState.kind === 'UNAUTHORIZED') {
                setFieldPreviewState({ kind: 'IDLE' });
                returnToCropCondition();
                return;
              }
              setAnalysisState({ kind: 'IDLE' });
              if ((analysisState.kind === 'ERROR' || analysisState.kind === 'UNAUTHORIZED') && analysisState.pendingAction === 'FIELD_CREATE') {
                returnToMyField();
              } else {
                safeSetViewStep('explore');
              }
            }}
            onLogin={() => safeSetViewStep('landing')}
          />
        } />

        {/* 5. Region Report Summary Screen */}
        <Route path={VIEW_STEP_PATH.report_summary} element={
          <RegionReportSummaryView
            regionName={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : `${selectedProvince} ${selectedDistrict}`}
            report={apiReport}
            onBack={() => safeSetViewStep(reportReturnStep)}
            onNext={() => safeSetViewStep('report_risks')}
            onOpenAIChat={() => setIsAIChatOpen(true)}
          />
        } />

        {/* 6. Region Risks Screen */}
        <Route path={VIEW_STEP_PATH.report_risks} element={
          <RegionRisksView
            report={apiReport}
            onBack={() => safeSetViewStep('report_summary')}
            onNext={() => safeSetViewStep('report_tips')}
          />
        } />

        {/* 7. Region Tips Screen */}
        <Route path={VIEW_STEP_PATH.report_tips} element={
          <RegionTipsView
            districtName={apiReport?.region?.sigunguName || selectedDistrict}
            report={apiReport}
            onBack={() => setViewStep(
              navigationFlow.kind === 'FIELD_REGISTRATION_TIPS' ? 'crop_suitability_report' : 'report_risks'
            )}
            variant={navigationFlow.kind === 'FIELD_REGISTRATION_TIPS' ? 'cropRegister' : (reportFlowSource === 'view' ? 'view' : 'default')}
            onRegisterCrop={() => {
              /* Already prepared a field via crop_suitability_report -> register
                 once with the existing preview instead of looping back to the
                 input screen. */
              if (navigationFlow.kind === 'FIELD_REGISTRATION_TIPS' && pendingCropRegistration) {
                void handleAddField();
                return;
              }
              setNavigationFlow({ kind: 'FIELD_REGISTRATION', returnTo: 'myfield' });
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
        } />

        {/* 8. Recommended Crops Screen */}
        <Route path={VIEW_STEP_PATH.recommended_crops} element={
          <RecommendedCropsView
            districtName={apiReport?.region?.sigunguName || selectedDistrict}
            report={apiReport}
            onBack={() => safeSetViewStep('report_tips')}
            onOpenAIChat={() => setIsAIChatOpen(true)}
            onSelectCrop={() => {
              // Picking a crop here means "register this region's field" --
              // mirrors RegionTipsView's onRegisterCrop rather than silently
              // discarding the selection by bouncing to MyField.
              setNavigationFlow({ kind: 'FIELD_REGISTRATION', returnTo: 'myfield' });
              setViewStep('condition');
            }}
          />
        } />

        {/* 9. Crop Suitability Report Screen (농작물 적합도 리포트) */}
        <Route path={VIEW_STEP_PATH.crop_suitability_report} element={
          <CropSuitabilityReportView
            fieldName={pendingCropRegistration?.fieldName}
            cropName={selectedCropName}
            fieldPreview={fieldPreview}
            onBack={returnToCropCondition}
            onRegisterCrop={isFieldRegistrationFlow && pendingCropRegistration ? handleAddField : returnToMyField}
            onOpenTips={() => {
              setNavigationFlow({ kind: 'FIELD_REGISTRATION_TIPS', returnTo: 'crop_suitability_report' });
              safeSetViewStep('report_tips');
            }}
          />
        } />

        {/* 10. Main Dashboard Screen */}
        <Route path={VIEW_STEP_PATH.dashboard} element={
          <MainDashboardView
            key={dailyKey}
            userName={userName}
            analyzedRegion={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : undefined}
            homeData={homeData}
            fields={myFields}
            loadError={homeLoadError}
            onGoToExplore={() => {
              setExploreReturnStep('dashboard');
              setReportReturnStep('dashboard');
              setExploreMode('analyze');
              safeSetViewStep('explore');
            }}
            onOpenReport={() => { void handleOpenConfirmedReport(undefined, 'dashboard'); }}
            onOpenAIChat={() => setIsAIChatOpen(true)}
            activeTab={activeTab}
            onTabChange={handleTabChange}
            isNewUser={isNewUser}
          />
        } />

        {/* 11. My Field List Screen */}
        <Route path={VIEW_STEP_PATH.myfield} element={
          <MyFieldListView
            fields={myFields}
            loadError={fieldLoadError}
            onAddField={openCropRegistrationFromMyField}
            onSelectField={handleSelectField}
            onOpenAIChat={() => setIsAIChatOpen(true)}
            activeTab={activeTab}
            onTabChange={handleTabChange}
          />
        } />

        {/* 11b. Field Detail Dashboard Screen */}
        <Route path="/field/:fieldId" element={
          selectedField ? (
            <FieldDashboardView
              field={selectedField}
            onBack={returnToMyField}
            onOpenAIChat={() => setIsAIChatOpen(true)}
            onDateChange={setFieldChatReportDate}
            onVisibleDataChange={handleFieldVisibleDataChange}
            />
          ) : null
        } />

        {/* 12. Community List Screen */}
        <Route path={VIEW_STEP_PATH.community} element={
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
        } />

        {/* 13. Community Create Screen */}
        <Route path={VIEW_STEP_PATH.community_create} element={
          <CommunityCreatePostView
            errorMessage={communityComposeError}
            onCancel={() => { setCommunityComposeError(null); safeSetViewStep('community'); }}
            onSubmitPost={handleCreatePost}
          />
        } />

        {/* 14. My Page (Settings) Screen */}
        <Route path={VIEW_STEP_PATH.mypage} element={
          <MyPageView
            userName={userName}
            userEmail={userEmail}
            userRegion={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : (selectedProvince && selectedDistrict ? `${selectedProvince} ${selectedDistrict}` : '지역 정보 없음')}
            posts={posts}
            onOpenAIChat={() => setIsAIChatOpen(true)}
            activeTab={activeTab}
            onTabChange={handleTabChange}
            onGoToExplore={() => {
              setExploreReturnStep('mypage');
              setReportReturnStep('mypage');
              setExploreMode('analyze');
              safeSetViewStep('explore');
            }}
            onUpdateUserName={setUserName}
            onLogout={handleLogout}
            onToggleLike={handleToggleLike}
            onToggleSave={handleToggleSave}
            onAddComment={handleAddComment}
          />
        } />

        {/* Unknown path: fall back to splash, which resolves to landing/dashboard once auth is known. */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      {/* 15. Global Farmflate AI Bottom Sheet Modal */}
      <AIChatModal
        isOpen={isAIChatOpen}
        onClose={() => setIsAIChatOpen(false)}
        context={{
          route: aiChatRouteFor(viewStep),
          regionAnalysisId: apiReport?.analysisId ?? null,
          fieldId: viewStep === 'field_dashboard' ? selectedField?.id ?? null : null,
          reportDate: viewStep === 'field_dashboard' ? fieldChatReportDate : null,
          visibleData: viewStep === 'field_dashboard'
            ? fieldVisibleData
            : aiChatRouteFor(viewStep) === 'region_report'
              ? buildRegionVisibleData(apiReport)
              : []
        }}
      />
    </div>
  );
}

export default App;
