import React, { useEffect } from 'react';
import { motion } from 'framer-motion';

interface SplashViewProps {
  onComplete: () => void;
}

export const SplashView: React.FC<SplashViewProps> = ({ onComplete }) => {
  useEffect(() => {
    // 2.2초 후 자동으로 다음 뷰로 넘어감
    const timer = setTimeout(() => {
      onComplete();
    }, 2200);
    return () => clearTimeout(timer);
  }, [onComplete]);

  return (
    <div className="full-screen-view" style={{ 
      backgroundColor: '#E9F7EC', 
      display: 'flex', 
      flexDirection: 'column', 
      alignItems: 'center', 
      justifyContent: 'center',
      height: '100vh',
      width: '100%'
    }}>
      <motion.div
        initial={{ opacity: 0, scale: 0.8 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.6, ease: "easeOut" }}
        style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}
      >
        {/* Main Logo Graphic (3 Circles based on screenshot) */}
        <div style={{ display: 'flex', gap: 12, marginBottom: 32, position: 'relative' }}>
          <motion.div 
            initial={{ y: -20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ delay: 0.2, type: "spring", stiffness: 200 }}
            style={{ position: 'absolute', top: -60, left: '50%', marginLeft: -24, width: 48, height: 48, borderRadius: '50%', backgroundColor: '#2FA86A' }} 
          />
          
          <motion.div 
            initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: 0.4 }}
            style={{ width: 40, height: 40, borderRadius: '50%', backgroundColor: '#80D4F6' }} 
          />
          <motion.div 
            initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: 0.5 }}
            style={{ width: 40, height: 40, borderRadius: '50%', backgroundColor: '#FBEB7C' }} 
          />
          <motion.div 
            initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: 0.6 }}
            style={{ width: 40, height: 40, borderRadius: '50%', backgroundColor: '#B8E5A7' }} 
          />
        </div>

        {/* Wordmark Title */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.8, duration: 0.5 }}
        >
          <img src="/svg-assets/brand/wordmark.svg" alt="Farmflate" style={{ height: 32 }} />
        </motion.div>
      </motion.div>
    </div>
  );
};
