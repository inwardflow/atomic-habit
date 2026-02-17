// Simple Web Audio API wrapper for generating ambient sounds
// No external assets required

class SoundEngine {
  private ctx: AudioContext | null = null;
  private whiteNoiseNode: AudioBufferSourceNode | null = null;
  private gainNode: GainNode | null = null;
  private isPlaying = false;
  private currentType: 'white' | 'pink' | 'brown' = 'white';

  constructor() {
    try {
      const AudioContextClass = (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext);
      if (AudioContextClass) {
        this.ctx = new AudioContextClass();
      }
    } catch (err) {
      console.error("Web Audio API not supported", err);
    }
  }

  private createNoiseBuffer() {
    if (!this.ctx) return null;
    const bufferSize = 2 * this.ctx.sampleRate;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const output = buffer.getChannelData(0);
    
    for (let i = 0; i < bufferSize; i++) {
      const white = Math.random() * 2 - 1;
      output[i] = (white + output[i]) / 2; // Simple smoothing
    }
    return buffer;
  }

  // Pink noise approximation (1/f) - softer, more rain-like
  private createPinkNoiseBuffer() {
    if (!this.ctx) return null;
    const bufferSize = 2 * this.ctx.sampleRate;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const output = buffer.getChannelData(0);
    let b0, b1, b2, b3, b4, b5, b6;
    b0 = b1 = b2 = b3 = b4 = b5 = b6 = 0.0;
    
    for (let i = 0; i < bufferSize; i++) {
      const white = Math.random() * 2 - 1;
      b0 = 0.99886 * b0 + white * 0.0555179;
      b1 = 0.99332 * b1 + white * 0.0750759;
      b2 = 0.96900 * b2 + white * 0.1538520;
      b3 = 0.86650 * b3 + white * 0.3104856;
      b4 = 0.55000 * b4 + white * 0.5329522;
      b5 = -0.7616 * b5 - white * 0.0168980;
      output[i] = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362;
      output[i] *= 0.11; // (roughly) compensate for gain
      b6 = white * 0.115926;
    }
    return buffer;
  }

  public async play(type: 'white' | 'rain' = 'rain') {
    if (!this.ctx) return;
    
    // Resume context if suspended (browser policy)
    if (this.ctx.state === 'suspended') {
      await this.ctx.resume();
    }

    if (this.isPlaying) {
      this.stop();
    }

    this.gainNode = this.ctx.createGain();
    this.gainNode.gain.setValueAtTime(0.01, this.ctx.currentTime);
    this.gainNode.gain.exponentialRampToValueAtTime(0.15, this.ctx.currentTime + 2); // Fade in
    this.gainNode.connect(this.ctx.destination);

    this.whiteNoiseNode = this.ctx.createBufferSource();
    this.whiteNoiseNode.buffer = type === 'rain' ? this.createPinkNoiseBuffer() : this.createNoiseBuffer();
    this.whiteNoiseNode.loop = true;
    this.whiteNoiseNode.connect(this.gainNode);
    this.whiteNoiseNode.start();
    
    this.isPlaying = true;
  }

  public stop() {
    if (this.whiteNoiseNode && this.isPlaying) {
      if (this.gainNode) {
         // Fade out
         try {
             this.gainNode.gain.exponentialRampToValueAtTime(0.001, this.ctx!.currentTime + 1);
         } catch { /* ignore */ }
      }
      
      const node = this.whiteNoiseNode;
      setTimeout(() => {
          try { node.stop(); } catch { /* ignore */ }
      }, 1000);
      
      this.isPlaying = false;
    }
  }

  public toggle() {
      if (this.isPlaying) this.stop();
      else this.play('rain');
  }

  public beep() {
      if (!this.ctx) return;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      
      osc.type = 'sine';
      osc.frequency.setValueAtTime(523.25, this.ctx.currentTime); // C5
      osc.frequency.exponentialRampToValueAtTime(1046.5, this.ctx.currentTime + 0.1); // C6
      
      gain.gain.setValueAtTime(0.1, this.ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, this.ctx.currentTime + 0.5);
      
      osc.start();
      osc.stop(this.ctx.currentTime + 0.5);
  }
}

export const soundEngine = new SoundEngine();
