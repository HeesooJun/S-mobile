interface PrimaryButtonProps {
  children: React.ReactNode;
  onClick?: () => void;
  variant?: 'red' | 'green' | 'gray';
  className?: string;
}

export function PrimaryButton({ 
  children, 
  onClick, 
  variant = 'gray',
  className = '' 
}: PrimaryButtonProps) {
  const colors = {
    red: 'bg-red-500/20 border-red-500 text-red-500 shadow-[0_0_20px_rgba(255,59,59,0.3)]',
    green: 'bg-green-500/20 border-green-400 text-green-400 shadow-[0_0_20px_rgba(0,255,159,0.3)]',
    gray: 'bg-white/5 border-gray-500 text-gray-300 shadow-[0_0_10px_rgba(255,255,255,0.1)]'
  };

  return (
    <button
      onClick={onClick}
      className={`px-8 py-3.5 rounded-full border-2 font-bold text-base ${colors[variant]} transition-all hover:scale-105 ${className}`}
    >
      {children}
    </button>
  );
}
