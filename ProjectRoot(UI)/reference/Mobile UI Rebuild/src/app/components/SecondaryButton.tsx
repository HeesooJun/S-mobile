interface SecondaryButtonProps {
  children: React.ReactNode;
  onClick?: () => void;
  variant?: 'red' | 'green' | 'gray';
  className?: string;
}

export function SecondaryButton({ 
  children, 
  onClick, 
  variant = 'gray',
  className = '' 
}: SecondaryButtonProps) {
  const colors = {
    red: 'bg-red-500/10 border-red-500/50 text-red-400',
    green: 'bg-green-500/10 border-green-400/50 text-green-300',
    gray: 'bg-white/3 border-gray-600 text-gray-400'
  };

  return (
    <button
      onClick={onClick}
      className={`px-6 py-2.5 rounded-full border font-medium text-sm ${colors[variant]} transition-all hover:scale-105 ${className}`}
    >
      {children}
    </button>
  );
}
