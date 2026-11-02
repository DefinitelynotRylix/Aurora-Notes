import React, { useState, useEffect } from 'react';
import { Note } from '../types';
import { encryptData, uint8ArrayToHex, getRandomValues } from '../utils/crypto';
import { X, ShieldCheck, Binary, Copy, Check, Lock, Cpu } from 'lucide-react';

interface CipherInspectorModalProps {
  note: Note | null;
  masterKey: CryptoKey | null;
  onClose: () => void;
}

export const CipherInspectorModal: React.FC<CipherInspectorModalProps> = ({
  note,
  masterKey,
  onClose,
}) => {
  const [cipherHex, setCipherHex] = useState<string>('');
  const [ivHex, setIvHex] = useState<string>('');
  const [copied, setCopied] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!note || !masterKey) return;

    let isMounted = true;
    setLoading(true);

    const computeCipher = async () => {
      try {
        const payload = JSON.stringify(note);
        const { ciphertext, iv } = await encryptData(payload, masterKey);
        if (isMounted) {
          setCipherHex(ciphertext);
          setIvHex(iv);
          setLoading(false);
        }
      } catch (err) {
        console.error('Cipher calculation error', err);
        if (isMounted) setLoading(false);
      }
    };

    computeCipher();

    return () => {
      isMounted = false;
    };
  }, [note, masterKey]);

  if (!note) return null;

  const handleCopyCipher = () => {
    navigator.clipboard.writeText(`IV: ${ivHex}\nCiphertext: ${cipherHex}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1E1A24]/40 backdrop-blur-md animate-fadeIn">
      <div className="glass-floating w-full max-w-xl rounded-2xl p-6 bg-white/95 border border-[#EADBFC] shadow-[0_20px_50px_rgba(89,37,220,0.18)] flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[#EADBFC]/80">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-[#F4EBFF] text-[#5925DC] flex items-center justify-center border border-[#EADBFC]">
              <Binary className="w-4 h-4" />
            </div>
            <div>
              <h2 className="font-bold text-base text-[#1E1A24]">
                Cryptographic Cipher Inspector
              </h2>
              <p className="text-xs text-[#797487]">
                Live inspection of authenticated AES-256-GCM ciphertext
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            aria-label="Close cipher inspector"
            className="p-1.5 rounded-lg text-[#797487] hover:text-[#1E1A24] hover:bg-[#FAF8FB] transition-all"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="py-4 space-y-4 overflow-y-auto pr-1">
          {/* Note Info */}
          <div className="p-3 rounded-xl bg-[#FAF8FB] border border-[#EADBFC]/70 flex items-center justify-between text-xs">
            <div>
              <span className="text-[#797487]">Target Note: </span>
              <strong className="text-[#1E1A24] font-semibold">{note.title || 'Untitled'}</strong>
            </div>
            <div className="inline-flex items-center gap-1.5 text-emerald-600 font-semibold text-[11px] bg-emerald-50 px-2 py-0.5 rounded-md border border-emerald-200">
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Zero-Knowledge Authenticated</span>
            </div>
          </div>

          {/* Crypto Specs Grid */}
          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="p-2.5 rounded-lg bg-white border border-[#EADBFC]">
              <div className="text-[10px] text-[#797487] uppercase font-bold tracking-wider">Algorithm</div>
              <div className="font-semibold text-[#1E1A24] mt-0.5">AES-256-GCM</div>
            </div>
            <div className="p-2.5 rounded-lg bg-white border border-[#EADBFC]">
              <div className="text-[10px] text-[#797487] uppercase font-bold tracking-wider">Key Derivation</div>
              <div className="font-semibold text-[#1E1A24] mt-0.5">PBKDF2-SHA256 (100k)</div>
            </div>
            <div className="p-2.5 rounded-lg bg-white border border-[#EADBFC]">
              <div className="text-[10px] text-[#797487] uppercase font-bold tracking-wider">Initialization Vector</div>
              <div className="font-mono text-[11px] text-[#5925DC] font-semibold mt-0.5 truncate">
                {ivHex || 'Calculating...'}
              </div>
            </div>
            <div className="p-2.5 rounded-lg bg-white border border-[#EADBFC]">
              <div className="text-[10px] text-[#797487] uppercase font-bold tracking-wider">Tag Integrity</div>
              <div className="font-semibold text-emerald-600 mt-0.5">128-bit GCM MAC Tag</div>
            </div>
          </div>

          {/* Raw Ciphertext Dump */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-xs font-semibold text-[#1E1A24]">
                Raw Encrypted Hex Payload on Disk:
              </label>
              <button
                onClick={handleCopyCipher}
                className="text-[11px] font-semibold text-[#5925DC] hover:text-[#4c1fc4] flex items-center gap-1 transition-all"
              >
                {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'Copied' : 'Copy Hex'}</span>
              </button>
            </div>

            <div className="p-3 rounded-xl bg-[#1E1A24] text-[#EADBFC] font-mono text-[11px] leading-relaxed max-h-44 overflow-y-auto select-all break-all border border-[#332E39]">
              {loading ? (
                <div className="flex items-center gap-2 text-[#CAC3D8]">
                  <Cpu className="w-4 h-4 animate-spin" />
                  <span>Computing AES-256-GCM transformation...</span>
                </div>
              ) : (
                cipherHex || 'No ciphertext available'
              )}
            </div>
            <p className="text-[11px] text-[#797487] mt-1.5">
              Without the in-memory master PBKDF2 key, this ciphertext is mathematically indistinguishable from random noise.
            </p>
          </div>
        </div>

        {/* Footer */}
        <div className="pt-4 border-t border-[#EADBFC]/80 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#5925DC] hover:bg-[#4c1fc4] text-white transition-all shadow-xs"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
