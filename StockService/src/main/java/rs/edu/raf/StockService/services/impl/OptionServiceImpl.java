package rs.edu.raf.StockService.services.impl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.webjars.NotFoundException;
import rs.edu.raf.StockService.data.dto.OptionDto;
import rs.edu.raf.StockService.data.entities.Option;
import rs.edu.raf.StockService.data.enums.OptionType;
import rs.edu.raf.StockService.mapper.OptionMapper;
import rs.edu.raf.StockService.repositories.OptionRepository;
import rs.edu.raf.StockService.services.OptionService;
import org.springframework.cache.annotation.Cacheable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OptionServiceImpl implements OptionService {

    private final OptionRepository optionRepository;
    private final OptionMapper optionMapper;

    public OptionServiceImpl(OptionRepository optionRepository, OptionMapper optionMapper) {

        this.optionRepository = optionRepository;
        this.optionMapper = optionMapper;
    }


    @Override
    public List<Option> findAll() {
        return optionRepository.findAll();
    }

   @Cacheable(value = "stockListing", key = "#stockListing")
    @Override
    public List<Option> findAllByStockListing(String stockListing) {

     //  List<Option> requestedOptions =  loadOptions(stockListing);

       List<Option> requestedOptions =  optionRepository.findAllByStockListing(stockListing);
//        if (requestedOptions.isEmpty()) {
//            requestedOptions =Optional.ofNullable(optionRepository.findAllByStockListing(stockListing));
//        }
//        if (requestedOptions.isEmpty()) {
//            throw new NotFoundException("Options for stock listing: " + stockListing + " not found.");
//        }

        return requestedOptions;
    }

    @Override
    public Option findById(Long id) {
        return optionRepository.findById(id).orElseThrow(() -> new NotFoundException("Option with id: " + id + " not found."));
    }


    @Override
    public Option findByStockListing(String stockListing) {
        return optionRepository.findByStockListing(stockListing);
    }

 //   @Scheduled(cron = "0 */15 * * * *") //every 15 minute
    public  List<Option> loadOptions(String stockListing) {


        String url = "https://query1.finance.yahoo.com/v6/finance/options/" + stockListing;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(responseBody);

            // Access data from the JSON object
            JSONObject optionChain = jsonResponse.getJSONObject("optionChain");
            JSONArray result = optionChain.getJSONArray("result");
            // Loop through the result array
            JSONObject option = result.getJSONObject(0);

            String underlyingSymbol = option.getString("underlyingSymbol");
            JSONArray expirationDates = option.getJSONArray("expirationDates");
            JSONArray strikes = option.getJSONArray("strikes");


            JSONObject quote = option.getJSONObject("quote");
            String language = quote.getString("language");
            String region = quote.getString("region");

            JSONArray options = option.getJSONArray("options");
            OptionDto optionDtoCall = new OptionDto();
            OptionDto optionDtoPut = new OptionDto();
            List<Option> optionList = new ArrayList<>();
            for (int i = 0; i < options.length(); i++) {
                JSONObject optionData = options.getJSONObject(i);
                long expirationDate = optionData.getLong("expirationDate");
                boolean hasMiniOptions = optionData.getBoolean("hasMiniOptions");
                // Access calls and puts arrays and extract data similarly
                JSONArray calls = optionData.getJSONArray("calls");
                JSONArray puts = optionData.getJSONArray("puts");
                LocalDate localDate = LocalDate.now();

                // Convert LocalDate to epoch time
                long epochTime = localDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond() * 1000;

                for (int j = 0; j < calls.length(); j++) {
                    JSONObject call = calls.getJSONObject(j);

                    String contractSymbol = call.getString("contractSymbol");

                    if (epochTime <= expirationDate) {
                        continue;
                    }
                    optionDtoCall.setStockListing(underlyingSymbol);
                    optionDtoCall.setSettlementDate(expirationDate);
                    optionDtoCall.setStrikePrice(call.getDouble("strike"));
                    optionDtoCall.setImpliedVolatility(call.getDouble("impliedVolatility"));
                    optionDtoCall.setOpenInterest(call.getDouble("openInterest"));
                    optionDtoCall.setOptionType(OptionType.CALL);
                    Option optionCall = optionMapper.optionDtoToOption(optionDtoCall);
            //        checkIfOptionExistsAndUpdate(optionCall);
                    optionList.add(optionCall);
                }
                for (int j = 0; j < puts.length(); j++) {
                    JSONObject put = puts.getJSONObject(j);
                    if (epochTime <= expirationDate) {
                        continue;
                    }
                    String contractSymbol = put.getString("contractSymbol");
                    optionDtoPut.setStockListing(underlyingSymbol);
                    optionDtoPut.setSettlementDate(expirationDate);
                    optionDtoPut.setStrikePrice(put.getDouble("strike"));
                    optionDtoPut.setImpliedVolatility(put.getDouble("impliedVolatility"));
                    optionDtoPut.setOpenInterest(put.getDouble("openInterest"));
                    optionDtoPut.setOptionType(OptionType.PUT);


                    Option optionPut = optionMapper.optionDtoToOption(optionDtoPut);
              //      checkIfOptionExistsAndUpdate(optionPut);
                    optionList.add(optionPut);

                }

            }
            return optionList;

        } catch (IOException | InterruptedException | JSONException e) {
          //  e.printStackTrace();

            return new ArrayList<>();
        }
    }


    @Override
    public void checkIfOptionExistsAndUpdate(Option option) {
        Optional<Option> option1 = optionRepository.findOption(option);
        if (option1.isPresent()) {
            option1.get().setOpenInterest(option.getOpenInterest());
            option1.get().setImpliedVolatility(option.getImpliedVolatility());
            optionRepository.save(option1.get());
        } else {
            optionRepository.save(option);
        }

    }


}

