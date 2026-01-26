import { Mic } from 'lucide-react';

interface MicButtonProps {
  onClick?: () => void;
  className?: string;
}

export function MicButton({ onClick, className = '' }: MicButtonProps) {
  return (
    <button
      onClick={onClick}
      className={`w-12 h-12 rounded-full border-2 border-green-400 bg-gray-900/50 flex items-center justify-center shadow-[0_0_15px_rgba(0,255,159,0.25)] transition-all hover:scale-105 ${className}`}
    >
      <Mic size={20} className="text-green-400" />
    </button>
  );
}
