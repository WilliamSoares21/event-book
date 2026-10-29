import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Component } from '@angular/core';
import { MenuSideBarComponent } from '../../../components/menu-side-bar/menu-side-bar';

interface EventLot {
  id: number;
  name: string;
  price: number | null;
  quantity: number | null;
  salesStart: string;
  salesEnd: string;
}

@Component({
  imports: [MenuSideBarComponent, RouterLink, FormsModule],
  selector: 'app-create-lots',
  styleUrl: './create-lots.css',
  templateUrl: './create-lots.html',
})
export class CreateLotsComponents {
  private nextLotId = 1;
  lots: EventLot[] = [];

  constructor() {
    this.addLot();
  }

  addLot() {
    this.lots.push({
      id: this.nextLotId++,
      name: '',
      price: null,
      quantity: null,
      salesStart: '',
      salesEnd: '',
    });
  }

  removeLot(id: number) {
    this.lots = this.lots.filter(lot => lot.id !== id);
  }


  createEvent() {
    
  }
}
