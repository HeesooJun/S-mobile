import { PrimaryButton } from './PrimaryButton';

interface Screen01Props {
  onYes?: () => void;
  onNo?: () => void;
  onRescuerMode?: () => void;
}

export function Screen01({ onYes, onNo, onRescuerMode }: Screen01Props) {
  return (
    <div className="w-[360px] h-[800px] bg-gradient-to-b from-gray-900 via-black to-black rounded-[32px] overflow-hidden shadow-2xl relative">
      {/* Vignette effect */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_0%,rgba(0,0,0,0.6)_100%)] pointer-events-none" />
      
      {/* Content */}
      <div className="relative h-full flex flex-col">
        {/* Header */}
        <div className="flex justify-between items-center px-6 pt-14 pb-4">
          <span className="text-white font-bold text-lg">Saivior</span>
          <span className="text-gray-400 text-sm">100%</span>
        </div>

        {/* Center Content */}
        <div className="flex-1 flex flex-col items-center justify-center px-8 -mt-16">
          {/* Logo Circle */}
          <div className="w-32 h-32 rounded-full bg-white/90 shadow-[0_0_40px_rgba(255,255,255,0.3)] mb-6" />
          
          {/* Title */}
          <h1 className="text-white font-extrabold text-3xl mb-2">Saivior</h1>
          <p className="text-gray-500 text-sm mb-12">오프라인 구조 시스템</p>

          {/* Rescuer Mode Button */}
          <PrimaryButton variant="gray" className="w-64 mb-6" onClick={onRescuerMode}>
            구조자 모드
          </PrimaryButton>

          {/* Divider */}
          <div className="w-full h-px bg-gradient-to-r from-transparent via-gray-700 to-transparent my-6" />

          {/* Emergency Question */}
          <p className="text-red-500 font-bold text-base mb-6">위급상황이신가요?</p>

          {/* Yes/No Buttons */}
          <div className="flex gap-4 w-full justify-center">
            <PrimaryButton variant="red" className="flex-1 max-w-[140px]" onClick={onYes}>
              YES
            </PrimaryButton>
            <PrimaryButton variant="gray" className="flex-1 max-w-[140px]" onClick={onNo}>
              NO
            </PrimaryButton>
          </div>
        </div>
      </div>
    </div>
  );
}
