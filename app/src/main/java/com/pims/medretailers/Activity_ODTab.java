//package com.example.medretailerpoc;
//
//import androidx.annotation.NonNull;
//import androidx.fragment.app.Fragment;
//import androidx.fragment.app.FragmentActivity;
//import androidx.viewpager2.adapter.FragmentStateAdapter;
//public class ViewPagerAdapter extends FragmentStateAdapter {
//
//    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
//        super (fragmentActivity);
//    }
//
//    @NonNull
//    @Override
//
//    public Fragment createFragment(int position) {
//        switch (position) {
//            case 0: return new OrderDetailsFragment();
//            case 1: return new ItemListFragment();
//            case 2: return new AddressFragment();
//            case 3:return new TermsFragment();
//            default: return new OrderDetailsFragment();
//
//        }
//    }
//
//    @Override
//    public int getItemCount() {
//        return 4;
//    }
//
//}
