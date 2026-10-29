import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  imports: [RouterLink, RouterLinkActive],
  selector: 'app-menu-side-bar',
  styleUrl: './menu-side-bar.css',
  templateUrl: './menu-side-bar.html',
})
export class MenuSideBarComponent {}
