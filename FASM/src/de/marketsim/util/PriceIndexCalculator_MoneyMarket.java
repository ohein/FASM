package de.marketsim.util;

/**
 * <p>�berschrift: Mircomarket Simulator</p>
 * <p>Beschreibung: </p>
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Organisation: </p>
 * @author Xining Wang
 * @version 1.0
 */

import java.util.*;
import jade.core.AID;
import java.io.*;
import org.apache.log4j.*;

import de.marketsim.SystemConstant;
import de.marketsim.config.*;
import de.marketsim.message.CashTrade_Order;
import de.marketsim.util.HelpTool;

public class PriceIndexCalculator_MoneyMarket extends PriceIndexCalculatorBase
{

  private double mKurs;
  private double mMaxTradeVolume  = 0;
  private int    mMaxTradeMenge      = 0;

  private double mFestTobintax_In_Cash1  = 0.0;
  private double mExtraTobintax_In_Cash1 = 0.0;

  private double mFestTobintax_In_Cash2  = 0.0;
  private double mExtraTobintax_In_Cash2 = 0.0;

  private DataFormatter nff  = new DataFormatter( Configurator.mConfData.mDataFormatLanguage );

  private Logger  mLogger = MsgLogger.getMsgLogger("PRICERECHNER");

 public PriceIndexCalculator_MoneyMarket( int pDay, boolean pAppend )
 {
   super( pDay, pAppend );
 }

 public double getPrice()
 {
   return this.mKurs;
 }

 public DailyOrderStatistic getDailyOrderStatistic()
 {
	 // Not implemented up to now for Money Market
	 return null;
 }

  /*
  Common Subprogramm
  Verteilen pMenge St�cke Atien zu den Orders mit dem pLimit
  pMenge wird verwendet, um den Rest zu pr�fen
  */

  private void AktienStuckVerteilen( Vector pOrderList,  double pKurs, double pVerteilRatio, int pTotalMenge )
  {
      int sum = 0;
      for ( int i=0; i< pOrderList.size(); i ++)
      {
         SingleOrder so = ( SingleOrder ) pOrderList.elementAt(i);
         if ( so.mLimit == pKurs )
         {
             so.mTradeMenge = (int) ( so.mMenge * pVerteilRatio );
         }
         sum = sum + so.mTradeMenge;
      }

      int rest = pTotalMenge - sum;
      if ( rest > 0 )
      {
        for ( int i=0; i<pOrderList.size(); i++)
        {
           SingleOrder so = ( SingleOrder ) pOrderList.elementAt(i);
           if ( so.mLimit == pKurs )
           {
               so.mTradeMenge++;
               // so.mTradeMenge = so.mTradeMenge + 1 ;
               rest--;
               // All Rest sind behandelt, Schleife braucht nicht weiter zu laufen.
               // gezwungen zu brechen
               if ( rest == 0 )
               {
                 break;
               }
           }
        }
      }
  }

  /*
  Common Subprogramm
  Behandel Orders und garantieren ihre Buy Menge
  */

  private void GarantierenBuyMenge( Vector pOrderList, double pKurs)
  {
      for ( int i=0; i< pOrderList.size(); i ++)
      {
         SingleOrder so = ( SingleOrder ) pOrderList.elementAt(i);
         if ( so.mLimit > pKurs )
         {
             so.mTradeMenge = so.mMenge;
         }
      }
  }

  /*
  Common Subprogramm
  Behandel Orders und garantieren ihre Sell Menge
  */

  private void GarantierenSellMenge( Vector pOrderList, double pKurs)
  {
      for ( int i=0; i< pOrderList.size(); i ++)
      {
         SingleOrder so = ( SingleOrder ) pOrderList.elementAt(i);
         if ( so.mLimit < pKurs )
         {
             so.mTradeMenge = so.mMenge;
         }
      }
  }

  public void calcindex()
  {
      //mFileLogger.println("Order overview: Buy-Order "  + this.mBuyOrderCounter + ", Sell-Order " + this.mSellOrderCounter  );
      //mFileLogger.println("AgentName; Type; Rule; OrderWish; Limit;  Menge"  );

      // for error debug
      mLogger.debug("Buy-Order:"       + this.mBuyOrderList.size() );
      mLogger.debug("CheapBuy-Order:"  + this.mCheapestBuyOrderList.size() );
      mLogger.debug("Sell-Order:"      + this.mSellOrderList.size() );
      mLogger.debug("BestSell-Order:"  + this.mBestenSellOrderList.size() );
      mLogger.debug("None-Order(wait):" + this.mNoneOrderList.size());

      displayAllOrderStatus( "INIT" );

      // According to the Limit, make sum of Buy-Orders and Sell Orders
      // then combine all sumed Buy-Sum and Sell-Sum into a CalcBase according
      // to Limit
      // process all Buy Orders

      for (int i=0; i<this.mBuyOrderList.size(); i++)
      {
        SingleOrder so = (SingleOrder) mBuyOrderList.elementAt(i);
        mLogger.debug("Buy-Order:" + so.mAID.getLocalName()+";Agenttype="+ so.mAgentType+";"+ so.mLimit +";"+ so.mMenge );

        if ( mLimitList.containsKey( new Double( so.mLimit )  ) )
        {
           PriceCalcBase onebase = (PriceCalcBase) mLimitList.get( new Double( so.mLimit ) );
           onebase.mBuyMenge = onebase.mBuyMenge + so.mMenge;
        }
        else
        {
           PriceCalcBase onebase = new PriceCalcBase( so.mLimit);
           onebase.mBuyMenge = so.mMenge;
           mCalcBase.add( onebase );
           mLimitList.put( new Double( so.mLimit  ), onebase );
        }
      }

      // process all Sell Orders
      for (int i=0; i<this.mSellOrderList.size(); i++)
      {
        SingleOrder so = (SingleOrder) mSellOrderList.elementAt(i);
        mLogger.debug("Sell-Order:" + so.mAID.getLocalName()+";Agenttype="+ so.mAgentType+";" + so.mLimit +";"+ so.mMenge );
        if ( mLimitList.containsKey( new Double( so.mLimit )  ) )
        {
           PriceCalcBase onebase = (PriceCalcBase) mLimitList.get( new Double( so.mLimit ) );
           onebase.mSellMenge = onebase.mSellMenge + so.mMenge;
        }
        else
        {
           PriceCalcBase onebase = new PriceCalcBase( so.mLimit );
           onebase.mSellMenge = so.mMenge;
           mCalcBase.add( onebase );
           mLimitList.put( new Double( so.mLimit  ), onebase );
        }
      }

      // make sume of all Cheapest Buy Orders
      for ( int i=0; i< this.mCheapestBuyOrderList.size(); i++)
      {
         SingleOrder so = (SingleOrder ) this.mCheapestBuyOrderList.elementAt(i);
         mLogger.debug("CheapBuy-Order:" + so.mAID.getLocalName()+";"+ so.mLimit +";"+ so.mMenge );
         this.mDailyOrderStatistic.mCheapestBuyMenge =  this.mDailyOrderStatistic.mCheapestBuyMenge + so.mMenge;
      }

      // make sume of all Besten Sell Orders
      for ( int i=0; i< this.mBestenSellOrderList.size(); i++)
      {
         SingleOrder so = (SingleOrder) this.mBestenSellOrderList.elementAt(i);
         mLogger.debug("BestSell-Order:" + so.mAID.getLocalName()+";"+ so.mLimit +";"+ so.mMenge );
         this.mDailyOrderStatistic.mBestenSellMenge = this.mDailyOrderStatistic.mBestenSellMenge + so.mMenge;
      }

      displaycontext( this.mCalcBase, "before sorting ---- " );

      // 1. Eintrag/Element mit kleinstem Limit
      // Letzte Eintrag/Element mit gro�estem Limit
      mCalcBase = DataSorting.AcendSorting( mCalcBase );

      displaycontext( this.mCalcBase, "after sorting ---- " );

      // save the count value to Original Sell/Buy Menge
      for ( int i=0; i< mCalcBase.size(); i++ )
      {
          PriceCalcBase pricecalcbase = ( PriceCalcBase) mCalcBase.elementAt( i );
          pricecalcbase.mOriginalBuyMenge  = pricecalcbase.mBuyMenge;
          pricecalcbase.mOriginalSellMenge = pricecalcbase.mSellMenge;
      }

      // Considering Cheapest and Best Menge
      // Add Cheapest Buy Meng to the last Element
      if ( mCalcBase.size() > 0 )
      {
          // Add Cheapest Buy Meng to the last Element
          PriceCalcBase lastcalcbase =
          ( PriceCalcBase) mCalcBase.elementAt( mCalcBase.size()-1 );
          lastcalcbase.mBuyMenge = lastcalcbase.mBuyMenge + this.mDailyOrderStatistic.mCheapestBuyMenge;

          // Add Besten Sell to the 1. Element
          PriceCalcBase firstcalcbase = ( PriceCalcBase) mCalcBase.elementAt( 0 );
          firstcalcbase.mSellMenge = firstcalcbase.mSellMenge +
                                     this.mDailyOrderStatistic.mBestenSellMenge;
      }

      displaycontext( this.mCalcBase, "After consideration of Cheapest-Buy and Best Sell" );

      // make sum of BuyMenge:  Von Last-Zeile zu 1. Zeile
      for ( int i=1; i< mCalcBase.size(); i++)
      {
          PriceCalcBase thisbase    =
                ( PriceCalcBase) mCalcBase.elementAt( mCalcBase.size()-i-1 );
          PriceCalcBase hisnextbase =
                ( PriceCalcBase) mCalcBase.elementAt( mCalcBase.size()-i );
          thisbase.mBuyMenge = thisbase.mBuyMenge + hisnextbase.mBuyMenge;
      }

      displaycontext( this.mCalcBase, "After BuyMenge is processed" );

      // make sum of SellMenge: Von 1. Zeile zu Last-Zeile
      for ( int i=1; i< mCalcBase.size(); i++)
      {
          PriceCalcBase lastbase = ( PriceCalcBase)mCalcBase.elementAt(i-1);
          PriceCalcBase thisbase = ( PriceCalcBase)mCalcBase.elementAt( i );
          thisbase.mSellMenge = thisbase.mSellMenge + lastbase.mSellMenge;
      }

     displaycontext( this.mCalcBase, "After SellMenge is Processed" );

     // B�seGesetz: Kurs aufgrund Maximal-TradeVolume bestimmt.
     mLogger.debug("Searching Max TradeVolume ---------" );
     //mFileLogger.println("Searching Max Trade volume ---------" );

     this.mMaxTradeVolume = 0;
     int position    = -1;

     //mFileLogger.println("Price, PossilbeBuyMenge, PossibleSellMenge, PossibleTradeMenge, PossibleTradeVolume" );
     mLogger.debug("Price, PossilbeBuyMenge, PossibleSellMenge, PossibleTradeMenge, PossibleTradeVolume" );

     boolean multimaximaltrademenge = false;

     for ( int i=0; i< mCalcBase.size(); i++)
     {
          PriceCalcBase thisbase =( PriceCalcBase) mCalcBase.elementAt( i );
          thisbase.mPossibleTradeMenge  = Math.min( thisbase.mBuyMenge, thisbase.mSellMenge );
          thisbase.mPossibleTradeVolume = thisbase.mPossibleTradeMenge * thisbase.mLimit;
          mLogger.debug( i+ ". Limit=" + thisbase.mLimit + "possible Menge="+ thisbase.mPossibleTradeMenge + " possible Volue="+ thisbase.mPossibleTradeVolume);
     }

     for ( int i=0; i< mCalcBase.size(); i++)
     {
          PriceCalcBase thisbase =( PriceCalcBase) mCalcBase.elementAt( i );

          /* Neue Theorie  */
          // es gibt die Situation:
          // Mehre Preise haben gleich Maximal Possible TradeVolume
          // In diesem Fall wird der Preis, der am n�hestenzum GerstenPreis liegt,
          // als neu Kurs gew�hlt.

          if ( thisbase.mPossibleTradeVolume > this.mMaxTradeVolume )
          {
              // der neue Preis mit MaximalTradeVolume
              multimaximaltrademenge = false;

              this.mMaxTradeVolume = thisbase.mPossibleTradeVolume;
              this.mKurs           = thisbase.mLimit;
              this.mUmsatz         = thisbase.mPossibleTradeMenge;
              position = i;
              mLogger.debug("gefunden MaxTradeVolume at " + i + ". Record");
              if ( thisbase.mBuyMenge == thisbase.mSellMenge )
              {
                this.mTradeStatus = SystemConstant.TradeResult_Bezahlt ;
              }
              else
              if ( thisbase.mBuyMenge > thisbase.mSellMenge )
              {
                this.mTradeStatus = SystemConstant.TradeResult_Geld;
              }
              else
              {
                this.mTradeStatus = SystemConstant.TradeResult_Brief;
              }
          }
          else
          if ( thisbase.mPossibleTradeMenge == mMaxTradeMenge )
          {
              multimaximaltrademenge = true;
          }

          /*
          mFileLogger.println( thisbase.mLimit +",  " +
                              thisbase.mBuyMenge + ",              " +
                              thisbase.mSellMenge + ",             " +
                              thisbase.mPossibleTradeMenge+",              "+
                              thisbase.mPossibleTradeVolume );
          */

          mLogger.debug("Current MaxTradeVolume= " + this.mMaxTradeVolume + " this Record PossibleTradeVolume=" + thisbase.mPossibleTradeVolume +  " at Record. No. =" +  position );
      }

      if ( multimaximaltrademenge )
      {
        //this.mFileLogger.println("There are multi-records with the same maximal TradeVolume=" + this.mMaxTradeVolume);
        mLogger.debug("There are multi MaxTradeVolume="+ this.mMaxTradeVolume  + " LastPrice=" + mLastPrice );
        mLogger.debug("The kurs has to be checked further: The kurs which is nearest to yesterday kurs will ne new kurs.");

        double deltapreis = Integer.MAX_VALUE;
        position = -1;

        for ( int i=0; i< mCalcBase.size(); i++)
        {
             PriceCalcBase thisbase =( PriceCalcBase) mCalcBase.elementAt( i );
             mLogger.debug( i+ ".MaxTradeVolume="+ thisbase.mPossibleTradeVolume + " position=" + position );

             if (  thisbase.mPossibleTradeVolume == this.mMaxTradeVolume  )
             {
                 if (  Math.abs( thisbase.mLimit - this.mLastPrice ) < deltapreis )
                 {
                   position = i;
                   deltapreis = Math.abs( thisbase.mLimit - this.mLastPrice );
                 }
             }
        }

        if ( ( position == -1 ) || ( this.mMaxTradeVolume == 0.0  ) )
        {
           this.mKurs   = this.mLastPrice;
           this.mUmsatz = 0;
           mLogger.debug("No Trade possible, let today' Kurs to yesterday's Kurs=" + this.mLastPrice );
           mLogger.debug("Final decision: New Kurs=" + this.mKurs );
        }
        else
        {
                PriceCalcBase thisbase = ( PriceCalcBase) mCalcBase.elementAt( position );
                this.mKurs             = thisbase.mLimit;
                this.mUmsatz           = thisbase.mPossibleTradeMenge;

                // 2006-10-11   "Bezahlt" wird nicht mehr ben�tigt.
                //if ( thisbase.mBuyMenge == thisbase.mSellMenge )
                //{
                //  this.mTradeStatus = SystemConstant.TradeResult_Bezahlt ;
                //}
                //else
                if ( thisbase.mBuyMenge >= thisbase.mSellMenge )
                {
                  this.mTradeStatus = SystemConstant.TradeResult_Geld;
                }
                else
                {
                  this.mTradeStatus = SystemConstant.TradeResult_Brief;
                }
                //this.mFileLogger.println("Final decision: new Kurs=" + this.mKurs + " at Record No.=" + position + " Info: Kurs vom Vortag=" + this.mLastPrice );
                mLogger.debug("Final decision: new Kurs =" + this.mKurs + " at Record No.=" + position + " Info: Kurs vom Vortag=" + this.mLastPrice);
        }
      };

      // check if Trade takes place.
      if ( this.mMaxTradeVolume == 0.0 )
      {
           // No Trade takes place.
           Vector AllOrders = this.getOrderList();
           // make sum of Buy-Menge und Sell-Menge
           int totalbuymenge  = 0;
           int totalsellmenge = 0;
           for (int i=0; i< AllOrders.size(); i++)
           {
              SingleOrder  so = (SingleOrder) AllOrders.elementAt(i);
              if ( so.mOrderWish == SystemConstant.WishType_Buy )
              {
                  totalbuymenge = totalbuymenge + so.mMenge;
              }
              else
              if ( so.mOrderWish == SystemConstant.WishType_Sell )
              {
                  totalsellmenge = totalsellmenge + so.mMenge;
              }
            }
            if ( totalbuymenge > totalsellmenge )
            {
              this.mTradeStatus = SystemConstant.TradeResult_Geld;
            }
            else
            if ( totalbuymenge < totalsellmenge )
            {
                this.mTradeStatus = SystemConstant.TradeResult_Brief;
            }
            else
            {
                this.mTradeStatus = SystemConstant.TradeResult_Taxe;
            }

           for (int i=0; i < AllOrders.size(); i++)
           {
              SingleOrder  so   = (SingleOrder) AllOrders.elementAt(i);
              so.mFinalKurs     = this.mKurs;
              so.mInvolvedCash1 = 0;
              so.mInvolvedExchange = false;
              so.mTradeMenge    = 0;
              so.mTradeResult   = this.mTradeStatus;

              CashTrade_Order original_order = (CashTrade_Order) this.mOriginalOrderList.get( so.mAID.getLocalName() );
              original_order.mFinalKurs     = this.mKurs;
              original_order.mBuyPerformed  = false;
              original_order.mSellPerformed = false;
              original_order.mTradeCash2    = 0;
              original_order.mInvolvedCash1 = 0;
              original_order.mTax_Extra     = 0;
              original_order.mTax_Fixed     = 0;
              original_order.mTradeResult = this.mTradeStatus;
           }

           for (int i=0; i< this.mNoneOrderList.size(); i++)
           {
              SingleOrder  so   = (SingleOrder) this.mNoneOrderList.elementAt(i);
              so.mFinalKurs     = this.mKurs;
              so.mInvolvedCash1 = 0;
              so.mTradeMenge    = 0;
              so.mTradeResult   = this.mTradeStatus;
              so.mInvolvedExchange = false;

              CashTrade_Order original_order = (CashTrade_Order) this.mOriginalOrderList.get( so.mAID.getLocalName() );
              original_order.mFinalKurs   = this.mKurs;
              original_order.mBuyPerformed  = false;
              original_order.mSellPerformed = false;
              original_order.mTradeCash2    = 0;
              original_order.mInvolvedCash1 = 0;
              original_order.mTax_Extra     = 0;
              original_order.mTax_Fixed     = 0;
              original_order.mTradeResult = this.mTradeStatus;

           }
      }
      else
      {
            // Trade is performed !!!!!!!!!!!!!!!!!!!

            //mFileLogger.println("Umsatz=" + this.mUmsatz + ", NeuKurs=" + this.mKurs + ", Kurszusatz=" + this.mTradeStatus + ", MaximalTradeVolume=" + this.mMaxTradeVolume );
            mLogger.debug("Now making Buymenge <--> Sell-menge distribution" );
            mLogger.debug("Umsatz=" + this.mUmsatz + ", NeuKurs=" + this.mKurs + ", Kurszusatz=" + this.mTradeStatus + ", MaximalTradeVolume=" + this.mMaxTradeVolume );

            if ( this.mTradeStatus == SystemConstant.TradeResult_Bezahlt  )
            {
                mLogger.debug("this.mTradeStatus=" + this.mTradeStatus+ "BuyMenge=SellMenge");

                this.GarantierenBuyMenge( this.mBuyOrderList, this.mKurs );
                this.GarantierenBuyMenge( this.mCheapestBuyOrderList, SystemConstant.Limit_CheapestBuy  );

                this.GarantierenSellMenge( this.mSellOrderList, this.mKurs );
                this.GarantierenSellMenge( this.mBestenSellOrderList, SystemConstant.Limit_BestenSell );

            }
            else
            if ( this.mTradeStatus == SystemConstant.TradeResult_Geld )
            {
                mLogger.debug("this.mTradeStatus=" + this.mTradeStatus+ "BuyMenge > SellMenge");

                this.GarantierenSellMenge( this.mSellOrderList, this.mKurs );
                this.GarantierenSellMenge( this.mBestenSellOrderList, SystemConstant.Limit_BestenSell );

                if ( this.mUmsatz < this.mDailyOrderStatistic.mCheapestBuyMenge )
                {
                  //Priority 1. Consider Cheapest Buy Order
                  mLogger.debug("Priority 1:  Umsatz < this.mCheapestBuyMenge");
                  double ratio = ( 1.0 * this.mUmsatz ) / this.mDailyOrderStatistic.mCheapestBuyMenge;
                  this.AktienStuckVerteilen( this.mCheapestBuyOrderList, SystemConstant.Limit_CheapestBuy, ratio, this.mUmsatz);
                }
                else
                if ( this.mUmsatz == this.mDailyOrderStatistic.mCheapestBuyMenge )
                {
                  //Priority 2.
                  mLogger.debug("Priority 2:  Umsatz = CheapestBuyMenge");
                  this.GarantierenBuyMenge( this.mCheapestBuyOrderList, SystemConstant.Limit_CheapestBuy );
                }
                else
                {
                    // Priority 3.  this.mUmsatz > this.mCheapestBuyMenge
                    mLogger.debug("Priority 3:  Umsatz > this.mCheapestBuyMenge");
                    // zuesrt garantieren CheapestBuyMenge
                    this.GarantierenBuyMenge( this.mCheapestBuyOrderList, SystemConstant.Limit_CheapestBuy );

                    // nehme CheapestBuyMenge aus letzte Zeile weg
                    // PriceCalcBase thisbase =( PriceCalcBase) mCalcBase.elementAt( mCalcBase.size()-1 );
                    // thisbase.mBuyMenge = thisbase.mBuyMenge - this.mCheapestBuyMenge;

                    PriceCalcBase thisbase;
                    int nochavailablemenge = this.mUmsatz - this.mDailyOrderStatistic.mCheapestBuyMenge;

                    int i = mCalcBase.size()-1 ;
                    do
                    {
                      thisbase =( PriceCalcBase) mCalcBase.elementAt( i );
                      if ( nochavailablemenge <= thisbase.mOriginalBuyMenge )
                      {
                         double ratio = (1.0*nochavailablemenge) / thisbase.mOriginalBuyMenge;
                         this.AktienStuckVerteilen( this.mBuyOrderList, thisbase.mLimit, ratio, nochavailablemenge );
                         nochavailablemenge = 0;
                      }
                      else
                      {
                         this.AktienStuckVerteilen( this.mBuyOrderList, thisbase.mLimit, 1.0, thisbase.mOriginalBuyMenge );
                         nochavailablemenge = nochavailablemenge - thisbase.mOriginalBuyMenge;
                         i--;
                      }
                    }
                    while (nochavailablemenge>0);
                }
            }
            else
            if ( this.mTradeStatus == SystemConstant.TradeResult_Brief )
            {

              // Gesamte Sell Menge > Gesamte Buy Menge
              this.GarantierenBuyMenge( this.mBuyOrderList, this.mKurs );
              this.GarantierenBuyMenge( this.mCheapestBuyOrderList, SystemConstant.Limit_CheapestBuy );

              // Verteil-Verfahren:

              //this.mFileLogger.println("BestenSellMenge=" + this.mBestenSellMenge );

              if ( this.mUmsatz < this.mDailyOrderStatistic.mBestenSellMenge )
              {
                 //Priority 1. Consider Cheapest Sell Order
                 mLogger.debug("Priority 1:  Umsatz < BestenSellMenge" );
                 double ratio = ( 1.0*this.mUmsatz ) / this.mDailyOrderStatistic.mBestenSellMenge;
                 this.AktienStuckVerteilen( this.mBestenSellOrderList,
                                            SystemConstant.Limit_BestenSell,
                                            ratio,
                                            this.mUmsatz);
              }
              else
              if ( this.mUmsatz == this.mDailyOrderStatistic.mBestenSellMenge )
              {
                 //Priority 2.
                 mLogger.debug("Priority 2:  Umsatz = BestenSellMenge");
                 this.GarantierenSellMenge( this.mBestenSellOrderList, SystemConstant.Limit_BestenSell );
              }
              else
              {
                //Priority 3.
                mLogger.debug("Priority 2:  Umsatz > BestenSellMenge");
                // zuesrt garantieren BestenSellMenge
                this.GarantierenSellMenge( this.mBestenSellOrderList, SystemConstant.Limit_BestenSell );

                // der Rest
                int nochavailablemenge = this.mUmsatz - this.mDailyOrderStatistic.mBestenSellMenge;
                //this.mFileLogger.println("After processed BestenSellMenge RestMenge=" + nochavailablemenge );

                // Verteilen nach Limit: Kleinest Sell-Limit wird zuerst ber�cksichtigt
                // dann weiter bis Umsatz fertig verteilt wird.

                // zuerst: Restore die originale SellMenge von 1. Zeile
                // Original SellMenge enth�lt keine BestenSell Menge.

                PriceCalcBase thisbase;
                // =( PriceCalcBase) mCalcBase.elementAt( 0 );
                //this.Logger.println("SellMenge of 1. CalcBase ="  + thisbase.mOriginalSellMenge );
                //thisbase.mSellMenge = thisbase.mSellMenge - this.mBestenSellMenge;
                //this.Logger.println("SellMenge of 1. CalcBase (after - BestenSellMenge) ="  + thisbase.mSellMenge );

                int i=0;
                do
                {
                    PriceCalcBase bb =( PriceCalcBase) mCalcBase.elementAt( i );
                    if ( nochavailablemenge >= bb.mOriginalSellMenge )
                    {
                       //this.Logger.println("Verteilen auf Orders mit Limit= " + bb.mLimit + " Ration=1.0  bb.originalSellMenge= " + bb.mOriginalSellMenge);
                       this.AktienStuckVerteilen( this.mSellOrderList,
                                                  bb.mLimit,
                                                  1.0,
                                                  bb.mOriginalSellMenge);
                       nochavailablemenge = nochavailablemenge - bb.mOriginalSellMenge;
                       //this.Logger.println("after verteilen auf Orders mit Limit= " + bb.mLimit + " Rest=="  + nochavailablemenge);
                    }
                    else
                    {
                        double ratio = (1.0*nochavailablemenge) / bb.mOriginalSellMenge;
                        //this.Logger.println("Verteilen auf Orders mit Limit= " + bb.mLimit + " Ration=" + ratio +"   bb.originalSellMenge= " + bb.mOriginalSellMenge );

                        this.AktienStuckVerteilen( this.mSellOrderList,
                                                   bb.mLimit,
                                                   ratio,
                                                   nochavailablemenge);
                        nochavailablemenge = 0;
                       //this.Logger.println("after verteilen auf Orders mit Limit= " + bb.mLimit + " Rest=="  + nochavailablemenge);
                    }
                    i++;
                }
                while ( nochavailablemenge > 0 ) ;
            }
         }
         // BuyMenge<-->SellMenge  distribution is finished;

         // TobinTax berechnen
         if ( Configurator.mConfData.mTobintaxAgentAktive )
         {
             //this.mFileLogger.println("Tobintax will be calculated" );
             //this.mFileLogger.println("PriceItems of PriceContainer= " + PriceContainer.getDataNumber() );

             //this.mFileLogger.println("Calculating Average Price from last " + Configurator.mConfData.mTobintax_Days4AverageKurs + " days ");
             double averagekurs = PriceContainer.getMovingAveragePrice( Configurator.mConfData.mTobintax_Days4AverageKurs );
             //this.mFileLogger.println("AveragePrice=" + averagekurs );

             //this.mFileLogger.println("Tobintax_Interventionsband =" + Configurator.mConfData.mTobintax_Interventionsband + "%");

             double interventionsband_obergrenz = averagekurs * ( 1 + Configurator.mConfData.mTobintax_Interventionsband / 100.0 );
             double interventionsband_untergrenz = averagekurs * ( 1 - Configurator.mConfData.mTobintax_Interventionsband / 100.0 );
             //this.mFileLogger.println("interventionsband_obengrenz=" + interventionsband_obergrenz );
             //this.mFileLogger.println("interventionsband_untergrenz=" + interventionsband_untergrenz );

             double  teilkurs_ausserhalb_interventionsband = 0.0;
             if ( this.mKurs > interventionsband_obergrenz )
             {
                 teilkurs_ausserhalb_interventionsband  = this.mKurs - interventionsband_obergrenz;
             }
             else
             if ( this.mKurs < interventionsband_untergrenz )
             {
                 teilkurs_ausserhalb_interventionsband = interventionsband_untergrenz - this.mKurs;
             }

             //this.mFileLogger.println("Kursteil_ausserhalb_band=" + teilkurs_ausserhalb_interventionsband );

             this.mLogger.debug( this.mDay + ". TOBINTAX-CALCULATION=====" );

             this.mLogger.debug("Kursteil_ausserhalb_band=" + teilkurs_ausserhalb_interventionsband );
             this.mLogger.debug("Tobintax: Feststeuer=" + Configurator.mConfData.mTobintax_FestSteuer +"% Extrasteuer=" + Configurator.mConfData.mTobintax_ExtraSteuer+"%" );

             Vector AllOrders = this.getOrderList();

             for (int i=0; i< AllOrders.size(); i++)
             {
                SingleOrder  so = (SingleOrder) AllOrders.elementAt(i);
                so.mFinalKurs   = this.mKurs;
                so.mTradeResult = this.mTradeStatus;

                if ( so.mAgentType == SystemConstant.AgentType_TobinTax )
                {
                   // Tobin tax Agent bezahlt selber keine Steuer
                   so.mInvolvedCash1 =  so.mTradeMenge / so.mFinalKurs ;

                   CashTrade_Order original_order = (CashTrade_Order) this.mOriginalOrderList.get( so.mAID.getLocalName() );
                   original_order.mFinalKurs      = this.mKurs;
                   original_order.mTradeResult    = this.mTradeStatus;
                   original_order.mInvolvedCash1  = so.mInvolvedCash1;
                   original_order.mTradeCash2     = so.mTradeMenge;

                   if ( so.mTradeMenge > 0 )
                   {
                     if ( so.mOrderWish == SystemConstant.WishType_Buy )
                     {
                       original_order.mBuyPerformed  = true;
                       original_order.mSellPerformed = false;
                     }
                     else
                     {
                       original_order.mBuyPerformed  = false;
                       original_order.mSellPerformed = true;
                     }
                   }
                }
                else
                {
                      if ( so.mOrderWish == SystemConstant.WishType_Buy )
                      {
                           // K�ufer
                           // ALLE WICHTIG !!!
                           // K�ufer bezahlt mehr als TradePreis.
                           // Die mehr bezahlte Geld wird in CASH2 umgerechnet

                           // Fixed Tax in CASH1
                           so.mTax_Fixed = so.mTradeMenge / so.mFinalKurs *
                                           Configurator.mConfData.mTobintax_FestSteuer / 100.0;
                           so.mTax_Fixed = HelpTool.DoubleTransfer(so.mTax_Fixed, 4);

                           // Extra Tax in CASH1
                           so.mTax_Extra = teilkurs_ausserhalb_interventionsband *
                                           so.mTradeMenge / so.mFinalKurs *
                                           Configurator.mConfData.mTobintax_ExtraSteuer / 100.0  ;
                           so.mTax_Extra = HelpTool.DoubleTransfer ( so.mTax_Extra, 4 );

                           mLogger.debug( so.getAgentName()+ "; Buyer;  Feststeuer="+ so.mTax_Fixed + " ExtraSteuer=" + so.mTax_Extra  );

                           // Final zu bezahlen: Tax + TradeMenge / Kurs;
                           so.mInvolvedCash1 = so.mTax_Fixed + so.mTax_Extra + so.mTradeMenge / so.mFinalKurs;
                           so.mInvolvedCash1 = HelpTool.DoubleTransfer ( so.mInvolvedCash1 , 4 ) ;

                           // dies Geld in CASH2 umrechnen und speichern
                           this.mFestTobintax_In_Cash2  = this.mFestTobintax_In_Cash2  + so.mTax_Fixed * so.mFinalKurs;
                           this.mExtraTobintax_In_Cash2 = this.mExtraTobintax_In_Cash2 + so.mTax_Extra * so.mFinalKurs;
                      }
                      else
                      {
                           // Verk�ufer
                           // ALLE WICHTIG !!!
                           // Verk�ufer bekommt nicht VOLLER CASH1, er zahlt TobinTax in CASH1

                           so.mTax_Fixed =  ( so.mTradeMenge / so.mFinalKurs ) *
                                            Configurator.mConfData.mTobintax_FestSteuer / 100.0;
                           so.mTax_Fixed = HelpTool.DoubleTransfer(so.mTax_Fixed, 4);

                           so.mTax_Extra = teilkurs_ausserhalb_interventionsband *
                                           ( so.mTradeMenge / so.mFinalKurs ) *
                                           Configurator.mConfData.mTobintax_ExtraSteuer / 100.0 ;
                           so.mTax_Extra = HelpTool.DoubleTransfer ( so.mTax_Extra, 4);

                           mLogger.debug( so.getAgentName()+ "; Seller;  Feststeuer="+ so.mTax_Fixed + " ExtraSteuer=" + so.mTax_Extra  );

                           // Final gekriegte Cash1
                           so.mInvolvedCash1 = so.mTradeMenge / so.mFinalKurs  - so.mTax_Fixed - so.mTax_Extra;
                           so.mInvolvedCash1 = HelpTool.DoubleTransfer( so.mInvolvedCash1 , 4);

                           this.mFestTobintax_In_Cash1  = this.mFestTobintax_In_Cash1  + so.mTax_Fixed ;
                           this.mExtraTobintax_In_Cash1 = this.mExtraTobintax_In_Cash1 + so.mTax_Extra ;
                      }

                      CashTrade_Order original_order = (CashTrade_Order) this.mOriginalOrderList.get( so.mAID.getLocalName() );
                      original_order.mFinalKurs     = this.mKurs;
                      original_order.mTradeResult   = this.mTradeStatus;

                      if ( so.mTradeMenge > 0 )
                      {
                        original_order.mTax_Fixed     = so.mTax_Fixed;
                        original_order.mTax_Extra     = so.mTax_Extra;
                        original_order.mInvolvedCash1 = so.mInvolvedCash1;
                        original_order.mTradeCash2    = so.mTradeMenge;
                        if ( so.mOrderWish == SystemConstant.WishType_Buy )
                        {
                          original_order.mBuyPerformed  = true;
                          original_order.mSellPerformed = false;
                        }
                        else
                        {
                          original_order.mBuyPerformed  = false;
                          original_order.mSellPerformed = true;
                        }
                      }
                      else
                      {
                         // No Updating on Original Order
                      }
                }
             }

             // Tobintax-Berechnen ist fertig !!!

             // write Tobintax to logfile
             try
             {
                 boolean newfile = false;
                 File ff = new File(Configurator.mConfData.getTobintaxCalculationDetailedLogFile());
                 if ( ! ff.exists() )
                 {
                   newfile = true;
                   System.out.println("Tobintax: Feststeuer=" + nff.format2str( Configurator.mConfData.mTobintax_FestSteuer  ) + " % ExtraSteuer=" + Configurator.mConfData.mTobintax_FestSteuer+ "%" );
                   System.out.println("Daily tobintax-calculation process is saved to " + Configurator.mConfData.getTobintaxCalculationDetailedLogFile() );
                 }

                 java.io.PrintWriter pw =
                 new java.io.PrintWriter (
                 new java.io.FileOutputStream( Configurator.mConfData.getTobintaxCalculationDetailedLogFile(), true ));

                 // write titele
                 if ( newfile )
                 {
                    pw.println( "Tobintax taeglich detailierte Berechnungsvorgang: TradeMenge ist immer in CASH2 gerechnet wie Aktien, deswegen TradeMenge ist immer ein int Wert. ");
                    pw.println( "Tobintax FestSteuer= "  + nff.format2str( Configurator.mConfData.mTobintax_FestSteuer )+ "%  ExtraSteuer=" + nff.format2str( Configurator.mConfData.mTobintax_ExtraSteuer ) +"%" );
                    pw.println( "Tag;Kurs;Trademenge(Cash2);AveragePrice_Last_" + Configurator.mConfData.mTobintax_Days4AverageKurs+  "_Days;Interventionsband_Untergrenz;Interventionsband_Obergrenz; Kursteil_AusserhalInterventionsband;FesteSteuer_in_Cash1;ExtraSteuer_in_Cash1;FesteSteuer_in_Cash2;ExtraSteuer_in_Cash2;" );
                 }

                String ss = this.mDay + ";" +
                            nff.format2str( this.mKurs ) + ";" +
                            this.mUmsatz + ";" +
                            nff.format2str( averagekurs ) + ";" +
                            nff.format2str( interventionsband_untergrenz )+";"+
                            nff.format2str( interventionsband_obergrenz )+";" +
                            nff.format2str( teilkurs_ausserhalb_interventionsband )+";" +
                            nff.format2str( this.mFestTobintax_In_Cash1  ) + ";" +
                            nff.format2str( this.mExtraTobintax_In_Cash1 ) + ";" +

                            nff.format2str( this.mFestTobintax_In_Cash2  ) + ";" +
                            nff.format2str( this.mExtraTobintax_In_Cash2 ) + ";" ;

                pw.println( ss  );
                pw.close();
                //System.out.println("Tobintax is saved to " + Configurator.mConfData.getTobintaxLogFile() );
             }
             catch (Exception ex)
             {

             }
         }
         else
         {
             //Keine Steuer erheben
             Vector AllOrders = this.getOrderList();
             for (int i=0; i< AllOrders.size(); i++)
             {
                SingleOrder  so = (SingleOrder) AllOrders.elementAt(i);
                so.mFinalKurs   = this.mKurs;
                so.mTradeResult = this.mTradeStatus;
                so.mInvolvedCash1 = HelpTool.DoubleTransfer( so.mTradeMenge / this.mKurs , 2 );

                CashTrade_Order original_order = (CashTrade_Order) this.mOriginalOrderList.get( so.mAID.getLocalName() );
                original_order.mFinalKurs   = this.mKurs;
                original_order.mTradeResult = this.mTradeStatus;

                if ( so.mInvolvedCash1 > 0.0 )
                {
                  if ( so.mOrderWish == SystemConstant.WishType_Buy )
                  {
                    original_order.mBuyPerformed  = true;
                    original_order.mSellPerformed = false;
                  }
                  else
                  {
                    original_order.mSellPerformed = true;
                    original_order.mBuyPerformed  = false;
                  }
                }
                original_order.mInvolvedCash1 = so.mInvolvedCash1;
                original_order.mTradeCash2    = so.mTradeMenge;
             }
         }

         for (int i=0; i< this.mNoneOrderList.size(); i++)
         {
           SingleOrder  so = (SingleOrder) this.mNoneOrderList.elementAt(i);
           so.mFinalKurs     = this.mKurs;
           so.mTax_Fixed     = 0;
           so.mTax_Extra     = 0;
           so.mInvolvedCash1 = 0;
           so.mTradeMenge    = 0;
           so.mTradeResult   = this.mTradeStatus;

           CashTrade_Order original_order = (CashTrade_Order) this.mOriginalOrderList.get( so.mAID.getLocalName() );
           original_order.mFinalKurs = this.mKurs;
           original_order.mTradeResult = this.mTradeStatus;
         }

      }
      DisplayDistribution();
      //mFileLogger.println( mDay + ". Log Ending -----------------------------------------");
      //mFileLogger.close();

      if ( this.mKurs == 0.0 )
      {
          System.out.println("*******************************************");
          System.exit(0);
      }
  }

  public double getMaxTradeVolume()
  {
    return this.mMaxTradeVolume;
  }

  public double  getFestTobintax_In_Cash1()
  {
    return this.mFestTobintax_In_Cash1;
  }

  public double  getExtraTobintax_In_Cash1()
  {
    return this.mExtraTobintax_In_Cash2;
  }

  public double getFestTobintax_In_Cash2()
  {
    return this.mFestTobintax_In_Cash2;
  }

  public double getExtraTobintax_In_Cash2()
  {
    return this.mExtraTobintax_In_Cash2;
  }

}
