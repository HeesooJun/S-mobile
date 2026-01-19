import { Mic } from 'lucide-react';

interface RecordButtonProps {
  state?: 'idle' | 'recording';
  onClick?: () => void;
  className?: string;
}

export function RecordButton({ state = 'idle', onClick, className = '' }: RecordButtonProps) {
  return (
    <button
      onClick={onClick}
      className={`w-11 h-11 rounded-full border-2 border-green-400 bg-gray-900/50 flex items-center justify-center shadow-[0_0_15px_rgba(0,255,159,0.25)] transition-all hover:scale-105 relative ${className}`}
    >
      <Mic size={18} className="text-green-400" />
      {state === 'recording' && (
        <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-red-500 border-2 border-gray-900 animate-pulse" />
      )}
    </button>
  );
}
