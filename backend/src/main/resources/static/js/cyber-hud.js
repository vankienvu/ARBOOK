/**
 * ARBook Cyber HUD Core Engine
 * Integrates Web Audio API, GSAP cursor tracking, magnetic buttons, and canvas particles.
 */

class CyberHUDEngine {
    constructor() {
        this.isMuted = localStorage.getItem('hud_muted') === 'true';
        this.audioCtx = null;
        this.cursorTrailer = null;
        this.mouseX = 0;
        this.mouseY = 0;
        
        window.addEventListener('DOMContentLoaded', () => {
            this.init();
        });
    }

    init() {
        // 1. Create Cyber UI Sound toggle button in the header/body
        this.createAudioToggleButton();
        
        // 2. Setup Cursor Trailer (only if desktop)
        if (window.innerWidth > 992) {
            this.createCursorTrailer();
        }

        // 3. Bind Event Listeners
        this.bindEvents();

        // 4. Setup Dynamic Background Particles
        this.initParticles();
    }

    createAudioToggleButton() {
        const toggle = document.createElement('button');
        toggle.id = 'hud-sound-toggle';
        toggle.className = 'btn btn-soft btn-sm px-2 py-1';
        toggle.style.position = 'fixed';
        toggle.style.bottom = '20px';
        toggle.style.right = '20px';
        toggle.style.zIndex = '9999';
        toggle.style.background = 'rgba(14, 9, 24, 0.8)';
        toggle.style.border = '1px solid rgba(168, 85, 247, 0.4)';
        toggle.style.borderRadius = '30px';
        toggle.style.fontSize = '0.75rem';
        toggle.style.display = 'flex';
        toggle.style.alignItems = 'center';
        toggle.style.gap = '6px';
        toggle.style.color = '#fff';
        toggle.style.boxShadow = '0 0 10px rgba(168, 85, 247, 0.2)';
        
        this.updateToggleState(toggle);
        
        toggle.addEventListener('click', (e) => {
            e.stopPropagation();
            this.isMuted = !this.isMuted;
            localStorage.setItem('hud_muted', this.isMuted);
            this.updateToggleState(toggle);
            if (!this.isMuted) {
                this.playSynthSound('success');
            }
        });

        document.body.appendChild(toggle);
    }

    updateToggleState(toggleBtn) {
        if (this.isMuted) {
            toggleBtn.innerHTML = '🔊 <span style="opacity: 0.6">HUD Audio: Off</span>';
            toggleBtn.style.borderColor = 'rgba(255, 255, 255, 0.1)';
        } else {
            toggleBtn.innerHTML = '🔊 <span>HUD Audio: On</span>';
            toggleBtn.style.borderColor = 'rgba(168, 85, 247, 0.6)';
        }
    }

    initAudioContext() {
        if (!this.audioCtx) {
            this.audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        }
        if (this.audioCtx.state === 'suspended') {
            this.audioCtx.resume();
        }
    }

    playSynthSound(type) {
        if (this.isMuted) return;
        try {
            this.initAudioContext();
            const ctx = this.audioCtx;
            
            const osc = ctx.createOscillator();
            const gainNode = ctx.createGain();
            osc.connect(gainNode);
            gainNode.connect(ctx.destination);
            
            const now = ctx.currentTime;
            
            if (type === 'hover') {
                // Short sci-fi high pitch click
                osc.type = 'sine';
                osc.frequency.setValueAtTime(800, now);
                osc.frequency.exponentialRampToValueAtTime(1400, now + 0.05);
                gainNode.gain.setValueAtTime(0.04, now);
                gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.05);
                osc.start(now);
                osc.stop(now + 0.05);
            } 
            else if (type === 'click') {
                // Tactical diagnostic click
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(400, now);
                osc.frequency.exponentialRampToValueAtTime(80, now + 0.08);
                gainNode.gain.setValueAtTime(0.12, now);
                gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.08);
                osc.start(now);
                osc.stop(now + 0.08);
            } 
            else if (type === 'success') {
                // Double chime (major arpeggio)
                osc.type = 'sine';
                osc.frequency.setValueAtTime(523.25, now); // C5
                osc.frequency.setValueAtTime(659.25, now + 0.06); // E5
                osc.frequency.setValueAtTime(783.99, now + 0.12); // G5
                gainNode.gain.setValueAtTime(0.06, now);
                gainNode.gain.setValueAtTime(0.06, now + 0.06);
                gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
                osc.start(now);
                osc.stop(now + 0.25);
            } 
            else if (type === 'scan') {
                // Holographic data sweep
                osc.type = 'sine';
                osc.frequency.setValueAtTime(150, now);
                osc.frequency.linearRampToValueAtTime(1200, now + 0.4);
                gainNode.gain.setValueAtTime(0.05, now);
                gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.4);
                osc.start(now);
                osc.stop(now + 0.4);
            }
            else if (type === 'error') {
                // Error buzz
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(120, now);
                osc.frequency.linearRampToValueAtTime(90, now + 0.2);
                gainNode.gain.setValueAtTime(0.08, now);
                gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.2);
                osc.start(now);
                osc.stop(now + 0.2);
            }
        } catch (e) {
            console.warn('Audio Context block:', e);
        }
    }

    createCursorTrailer() {
        this.cursorTrailer = document.createElement('div');
        this.cursorTrailer.id = 'hud-cursor-trailer';
        this.cursorTrailer.style.position = 'fixed';
        this.cursorTrailer.style.width = '24px';
        this.cursorTrailer.style.height = '24px';
        this.cursorTrailer.style.border = '1px solid rgba(168, 85, 247, 0.6)';
        this.cursorTrailer.style.borderRadius = '50%';
        this.cursorTrailer.style.pointerEvents = 'none';
        this.cursorTrailer.style.transform = 'translate(-50%, -50%)';
        this.cursorTrailer.style.zIndex = '99999';
        this.cursorTrailer.style.transition = 'width 0.2s, height 0.2s, background-color 0.2s, border-color 0.2s';
        this.cursorTrailer.style.mixBlendMode = 'screen';
        this.cursorTrailer.style.boxShadow = '0 0 8px rgba(168, 85, 247, 0.3)';
        
        // Inner cursor dot
        const dot = document.createElement('div');
        dot.style.position = 'absolute';
        dot.style.top = '50%';
        dot.style.left = '50%';
        dot.style.width = '4px';
        dot.style.height = '4px';
        dot.style.borderRadius = '50%';
        dot.style.backgroundColor = '#ec4899';
        dot.style.transform = 'translate(-50%, -50%)';
        this.cursorTrailer.appendChild(dot);
        
        document.body.appendChild(this.cursorTrailer);

        document.addEventListener('mousemove', (e) => {
            this.mouseX = e.clientX;
            this.mouseY = e.clientY;
            
            // GSAP smooth follow
            if (window.gsap) {
                gsap.to(this.cursorTrailer, {
                    x: this.mouseX,
                    y: this.mouseY,
                    duration: 0.15,
                    ease: "power2.out"
                });
            }
        });
    }

    bindEvents() {
        // Watch for hovering interactive items
        document.body.addEventListener('mouseenter', (e) => {
            const target = e.target.closest('a, button, .magnetic-btn, .hover-lift, [role="button"]');
            if (target) {
                this.playSynthSound('hover');
                if (this.cursorTrailer) {
                    this.cursorTrailer.style.width = '42px';
                    this.cursorTrailer.style.height = '42px';
                    this.cursorTrailer.style.borderColor = 'rgba(236, 72, 153, 0.9)';
                    this.cursorTrailer.style.backgroundColor = 'rgba(168, 85, 247, 0.15)';
                    this.cursorTrailer.style.boxShadow = '0 0 15px rgba(236, 72, 153, 0.6)';
                }
            }
        }, true);

        document.body.addEventListener('mouseleave', (e) => {
            const target = e.target.closest('a, button, .magnetic-btn, .hover-lift, [role="button"]');
            if (target && this.cursorTrailer) {
                this.cursorTrailer.style.width = '24px';
                this.cursorTrailer.style.height = '24px';
                this.cursorTrailer.style.borderColor = 'rgba(168, 85, 247, 0.6)';
                this.cursorTrailer.style.backgroundColor = 'transparent';
                this.cursorTrailer.style.boxShadow = '0 0 8px rgba(168, 85, 247, 0.3)';
            }
        }, true);

        document.body.addEventListener('click', (e) => {
            const target = e.target.closest('a, button, .magnetic-btn, [role="button"]');
            if (target) {
                this.playSynthSound('click');
                // Mini pulse animation on cursor
                if (this.cursorTrailer && window.gsap) {
                    gsap.fromTo(this.cursorTrailer, 
                        { scale: 0.8, opacity: 0.5 }, 
                        { scale: 1.4, opacity: 1, duration: 0.2, ease: "power1.out" }
                    );
                }
            }
        });

        // Setup magnetic elements
        this.bindMagneticElements();
    }

    bindMagneticElements() {
        const updateMagnetic = () => {
            const elements = document.querySelectorAll('.magnetic-btn, .btn-primary, .btn-soft');
            elements.forEach(btn => {
                if (btn.classList.contains('magnetic-bound')) return;
                btn.classList.add('magnetic-bound');

                btn.addEventListener('mousemove', (e) => {
                    const bound = btn.getBoundingClientRect();
                    const x = e.clientX - bound.left - (bound.width / 2);
                    const y = e.clientY - bound.top - (bound.height / 2);
                    
                    if (window.gsap) {
                        gsap.to(btn, {
                            x: x * 0.35,
                            y: y * 0.35,
                            duration: 0.3,
                            ease: "power2.out"
                        });
                    }
                });
                
                btn.addEventListener('mouseleave', () => {
                    if (window.gsap) {
                        gsap.to(btn, {
                            x: 0,
                            y: 0,
                            duration: 0.5,
                            ease: "elastic.out(1, 0.3)"
                        });
                    }
                });
            });
        };

        updateMagnetic();
        // Re-bind when DOM updates (for SPA elements/Dynamic elements)
        const observer = new MutationObserver(updateMagnetic);
        observer.observe(document.body, { childList: true, subtree: true });
    }

    initParticles() {
        const canvas = document.getElementById('space-canvas');
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        let particles = [];
        const particleCount = 60;

        const resizeCanvas = () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        };

        window.addEventListener('resize', resizeCanvas);
        resizeCanvas();

        class Particle {
            constructor() {
                this.reset();
                this.y = Math.random() * canvas.height;
            }

            reset() {
                this.x = Math.random() * canvas.width;
                this.y = -10;
                this.size = Math.random() * 2 + 0.5;
                this.speedY = Math.random() * 0.3 + 0.15;
                this.speedX = (Math.random() - 0.5) * 0.2;
                this.color = Math.random() > 0.5 
                    ? `rgba(168, 85, 247, ${Math.random() * 0.3 + 0.1})` 
                    : `rgba(236, 72, 153, ${Math.random() * 0.3 + 0.1})`;
            }

            update() {
                this.y += this.speedY;
                this.x += this.speedX;
                if (this.y > canvas.height + 10 || this.x < -10 || this.x > canvas.width + 10) {
                    this.reset();
                }
            }

            draw() {
                ctx.fillStyle = this.color;
                ctx.beginPath();
                ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
                ctx.fill();
            }
        }

        for (let i = 0; i < particleCount; i++) {
            particles.push(new Particle());
        }

        const animate = () => {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            particles.forEach(p => {
                p.update();
                p.draw();
            });
            requestAnimationFrame(animate);
        };

        animate();
    }
}

// Instantiate global engine
window.cyberHUD = new CyberHUDEngine();
