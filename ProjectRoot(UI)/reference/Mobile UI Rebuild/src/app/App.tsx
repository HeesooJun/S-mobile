import { useCallback, useMemo, useState } from 'react';
import { Screen01 } from '@/app/components/Screen01';
import { Screen02 } from '@/app/components/Screen02';
import { Screen03 } from '@/app/components/Screen03';
import { Screen04 } from '@/app/components/Screen04';
import { Screen05 } from '@/app/components/Screen05';

export default function App() {
  const [viewMode, setViewMode] = useState<'flow' | 'grid'>('flow');
  const [activeIndex, setActiveIndex] = useState(0);

  const goFirst = useCallback(() => setActiveIndex(0), []);

  type FlowHandlers = {
    next: () => void;
    prev: () => void;
  };

  const screens = useMemo(
    () => [
      { label: 'Screen 01', render: ({ next }: FlowHandlers) => <Screen01 onYes={next} /> },
      { label: 'Screen 02', render: ({ next, prev }: FlowHandlers) => (
        <Screen02 onPrev={prev} onSos={next} />
      ) },
      { label: 'Screen 03', render: ({ next, prev }: FlowHandlers) => (
        <Screen03 onPrev={prev} onNext={next} />
      ) },
      { label: 'Screen 04', render: ({ next }: FlowHandlers) => (
        <Screen04 onDisconnect={goFirst} onChat={next} />
      ) },
      { label: 'Screen 05', render: ({ prev }: FlowHandlers) => <Screen05 onPrev={prev} /> }
    ],
    [goFirst]
  );

  const total = screens.length;
  const canPrev = activeIndex > 0;
  const canNext = activeIndex < total - 1;

  const goPrev = () => setActiveIndex((index) => (index > 0 ? index - 1 : index));
  const goNext = () => setActiveIndex((index) => (index < total - 1 ? index + 1 : index));

  return (
    <div className="min-h-screen bg-black p-8">
      <div className="max-w-[1920px] mx-auto">
        <h1 className="text-white text-4xl font-black mb-4 text-center">
          LifeSavior UI Rebuild
        </h1>

        <div className="flex items-center justify-center gap-3 mb-8">
          <button
            onClick={() => setViewMode('flow')}
            className={`px-5 py-2 rounded-full border text-sm font-semibold transition-all ${
              viewMode === 'flow'
                ? 'border-green-400 text-green-300 bg-green-500/10 shadow-[0_0_15px_rgba(0,255,159,0.2)]'
                : 'border-gray-700 text-gray-500 hover:text-gray-300'
            }`}
          >
            순차 보기
          </button>
          <button
            onClick={() => setViewMode('grid')}
            className={`px-5 py-2 rounded-full border text-sm font-semibold transition-all ${
              viewMode === 'grid'
                ? 'border-green-400 text-green-300 bg-green-500/10 shadow-[0_0_15px_rgba(0,255,159,0.2)]'
                : 'border-gray-700 text-gray-500 hover:text-gray-300'
            }`}
          >
            전체 보기
          </button>
        </div>

        {viewMode === 'flow' ? (
          <div className="flex flex-col items-center min-h-[calc(100vh-240px)]">
            <div className="text-gray-500 text-sm font-bold mb-4">
              {screens[activeIndex].label} · {activeIndex + 1}/{total}
            </div>

            <div className="mb-6">
              {screens[activeIndex].render({ next: goNext, prev: goPrev })}
            </div>

            <div className="flex flex-col items-center gap-3 mt-auto pb-2">
              <span className="text-xs font-semibold tracking-wide text-gray-600 uppercase">
                Dev-only navigation
              </span>
              <button
                onClick={goPrev}
                disabled={!canPrev}
                className={`px-4 py-2 rounded-full border text-sm font-semibold transition-all ${
                  canPrev
                    ? 'border-gray-600 text-gray-300 hover:scale-105'
                    : 'border-gray-800 text-gray-600 opacity-50 cursor-not-allowed'
                }`}
              >
                이전
              </button>
              <button
                onClick={goNext}
                disabled={!canNext}
                className={`px-4 py-2 rounded-full border text-sm font-semibold transition-all ${
                  canNext
                    ? 'border-green-500 text-green-300 hover:scale-105'
                    : 'border-gray-800 text-gray-600 opacity-50 cursor-not-allowed'
                }`}
              >
                다음
              </button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-8 place-items-center">
            <div>
              <h2 className="text-gray-400 text-sm font-bold mb-3 text-center">Screen 01</h2>
              <Screen01 />
            </div>

            <div>
              <h2 className="text-gray-400 text-sm font-bold mb-3 text-center">Screen 02</h2>
              <Screen02 />
            </div>

            <div>
              <h2 className="text-gray-400 text-sm font-bold mb-3 text-center">Screen 03</h2>
              <Screen03 />
            </div>

            <div>
              <h2 className="text-gray-400 text-sm font-bold mb-3 text-center">Screen 04</h2>
              <Screen04 />
            </div>

            <div>
              <h2 className="text-gray-400 text-sm font-bold mb-3 text-center">Screen 05</h2>
              <Screen05 />
            </div>
          </div>
        )}

        {/* Component Variants Demo */}
        <div className="mt-16 p-8 bg-gray-900/50 rounded-xl border border-gray-800">
          <h2 className="text-white text-2xl font-bold mb-6">Component Variants</h2>
          
          <div className="space-y-8">
            {/* BatteryIndicator variants */}
            <div>
              <h3 className="text-gray-400 text-sm font-bold mb-3">BatteryIndicator</h3>
              <div className="flex gap-6 items-center">
                <div className="text-center">
                  <div className="mb-2">
                    <div className="inline-flex flex-col items-center">
                      <div className="relative w-11 h-6 rounded-sm border-2 flex items-center justify-start p-0.5" style={{ borderColor: '#00ff9f' }}>
                        <div className="h-full rounded-sm transition-all" style={{ width: '36px', backgroundColor: '#00ff9f' }} />
                      </div>
                      <div className="w-1 h-3 rounded-r absolute right-[-5px] top-[6px]" style={{ backgroundColor: '#00ff9f' }} />
                    </div>
                  </div>
                  <span className="text-gray-500 text-xs">High (92%)</span>
                </div>
                <div className="text-center">
                  <div className="mb-2">
                    <div className="inline-flex flex-col items-center">
                      <div className="relative w-11 h-6 rounded-sm border-2 flex items-center justify-start p-0.5" style={{ borderColor: '#fbbf24' }}>
                        <div className="h-full rounded-sm transition-all" style={{ width: '18px', backgroundColor: '#fbbf24' }} />
                      </div>
                      <div className="w-1 h-3 rounded-r absolute right-[-5px] top-[6px]" style={{ backgroundColor: '#fbbf24' }} />
                    </div>
                  </div>
                  <span className="text-gray-500 text-xs">Mid (50%)</span>
                </div>
                <div className="text-center">
                  <div className="mb-2">
                    <div className="inline-flex flex-col items-center">
                      <div className="relative w-11 h-6 rounded-sm border-2 flex items-center justify-start p-0.5" style={{ borderColor: '#ff3b3b' }}>
                        <div className="h-full rounded-sm transition-all" style={{ width: '7px', backgroundColor: '#ff3b3b' }} />
                      </div>
                      <div className="w-1 h-3 rounded-r absolute right-[-5px] top-[6px]" style={{ backgroundColor: '#ff3b3b' }} />
                    </div>
                  </div>
                  <span className="text-gray-500 text-xs">Low (20%)</span>
                </div>
              </div>
            </div>

            {/* RecordButton variants */}
            <div>
              <h3 className="text-gray-400 text-sm font-bold mb-3">RecordButton</h3>
              <div className="flex gap-6 items-center">
                <div className="text-center">
                  <div className="mb-2">
                    <button className="w-11 h-11 rounded-full border-2 border-green-400 bg-gray-900/50 flex items-center justify-center shadow-[0_0_15px_rgba(0,255,159,0.25)] relative">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-green-400">
                        <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
                        <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                        <line x1="12" x2="12" y1="19" y2="22"/>
                      </svg>
                    </button>
                  </div>
                  <span className="text-gray-500 text-xs">Idle</span>
                </div>
                <div className="text-center">
                  <div className="mb-2">
                    <button className="w-11 h-11 rounded-full border-2 border-green-400 bg-gray-900/50 flex items-center justify-center shadow-[0_0_15px_rgba(0,255,159,0.25)] relative">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-green-400">
                        <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
                        <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                        <line x1="12" x2="12" y1="19" y2="22"/>
                      </svg>
                      <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-red-500 border-2 border-gray-900 animate-pulse" />
                    </button>
                  </div>
                  <span className="text-gray-500 text-xs">Recording</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
