import { Component, ElementRef, afterRenderEffect, inject, viewChild, effect, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { SessionService } from '../../services/session/session';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './navbar.html',
  styleUrls: ['./navbar.css']
})
export class NavbarComponent {
  readonly session = inject(SessionService);
  private readonly router = inject(Router);
  
  // Reference to the dropdown trigger button (optional because it's conditionally rendered)
  readonly dropdownTrigger = viewChild<ElementRef<HTMLButtonElement>>('dropdownTrigger');
  
  // Track dropdown initialization
  private dropdownInitialized = signal(false);

  constructor() {
    // Initialize Bootstrap dropdown when the trigger element is rendered and user is logged in
    effect(() => {
      const trigger = this.dropdownTrigger();
      const user = this.session.user();
      console.log('[Navbar] Effect triggered:', { hasTrigger: !!trigger, user: user?.name, initialized: this.dropdownInitialized() });
      
      if (trigger && user && typeof window !== 'undefined' && (window as any).bootstrap) {
        // Check if dropdown is already initialized
        if (!trigger.nativeElement.hasAttribute('data-bs-toggle-initialized')) {
          const dropdown = new (window as any).bootstrap.Dropdown(trigger.nativeElement);
          trigger.nativeElement.setAttribute('data-bs-toggle-initialized', 'true');
          this.dropdownInitialized.set(true);
          console.log('[Navbar] Bootstrap dropdown initialized');
          
          // Also add a click handler to ensure toggle works
          trigger.nativeElement.addEventListener('click', (event) => {
            console.log('[Navbar] Button clicked, toggling dropdown');
            dropdown.toggle();
          });
        }
      } else if (!user) {
        this.dropdownInitialized.set(false);
      }
    });
  }

  logout() {
    this.session.clear();
    this.dropdownInitialized.set(false);
    void this.router.navigate(['/login']);
  }
}
